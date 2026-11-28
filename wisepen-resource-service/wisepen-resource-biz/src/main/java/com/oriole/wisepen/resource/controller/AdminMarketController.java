package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleAuditRequest;
import com.oriole.wisepen.resource.domain.dto.res.MarketOrderResponse;
import com.oriole.wisepen.resource.domain.dto.res.MarketSearchHitItemResponse;
import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.SearchScope;
import com.oriole.wisepen.resource.service.IMarketService;
import com.oriole.wisepen.resource.service.ISearchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员 - 集市", description = "平台管理员审核集市销售信息")
@RestController
@RequestMapping("/admin/market")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminMarketController {

    private final IMarketService marketService;
    private final ISearchQueryService searchQueryService;

    @Operation(
            summary = "搜索集市资源快照",
            description = """
                    - 用途：平台管理员检索集市售卖资源快照，用于审核、下架、封禁和排查。
                    - 请求：keyword 为搜索关键字，可为空；marketGroupId 可选；scope 指定搜索范围；status 可选，不传时返回全部售卖状态；page 和 size 控制分页。
                    - 约束：当前用户必须是平台管理员；scope 必须是合法枚举；status 传入时必须是合法 MarketSaleStatus。
                    - 处理：查询集市专用搜索索引，按 status 决定检索哪些售卖状态；检索资源名、可预览范围内的 previewContent 和内部 searchContent；仅对资源名和 previewContent 做高亮；不复用普通资源 ACL；不返回 ES 技术主键和 searchContent。
                    - 失败：当前身份不是平台管理员 -> PermissionError.UNAUTHORIZED；搜索服务执行失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：返回分页搜索命中列表和总数，命中项包含 resourceId、marketGroupId、resourceType、resourceName、ownerId、marketSaleInfo、highlightContent 和 updateTime。
                    """
    )
    @Log(title = "管理员搜索集市资源", businessType = BusinessType.SELECT, isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/searchMarketResources")
    public R<PageR<MarketSearchHitItemResponse>> searchMarketResources(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "marketGroupId", required = false) String marketGroupId,
            @RequestParam(value = "scope") SearchScope scope,
            @RequestParam(value = "status", required = false) MarketSaleStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(searchQueryService.marketSearch(
                keyword,
                marketGroupId,
                scope,
                status,
                page,
                size
        ));
    }

    @Operation(
            summary = "分页查询集市订单",
            description = """
                    - 用途：供平台管理员查询集市交易订单。
                    - 请求：userId 可选，指定后按买家筛选订单；page 和 size 控制分页，均从 1 开始。
                    - 约束：当前用户必须是平台管理员；page 和 size 必须大于等于 1。
                    - 处理：传入 userId 时按买家分页查询；未传 userId 时按创建时间倒序分页读取全部订单；不修改订单、买家或卖家数据。
                    - 失败：当前身份不是平台管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：返回订单分页，包含订单 ID、买家、卖家、资源、集市组、购买版本、支付价格和创建时间。
                    """
    )
    @Log(title = "管理员查询集市订单", businessType = BusinessType.SELECT, isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/listOrders")
    public R<PageR<MarketOrderResponse>> listOrders(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        return R.ok(marketService.listOrders(userId, page, size));
    }

    @Operation(
            summary = "审核资源",
            description = """
                    - 用途：平台管理员审核资源在指定集市组中的整组销售信息。
                    - 请求：resourceId 指定资源；marketGroupId 使用原始集市组ID，定位该资源在目标集市组中的销售信息；offerVersion 必须匹配当前提交版本；status 是审核后写入的目标状态；auditMessage 是审核说明。
                    - 约束：当前用户必须是平台管理员；集市销售信息必须存在；offerVersion 必须与当前提交版本一致；status 为 REJECTED 或 BANNED 时必须填写 auditMessage。
                    - 处理：写入 marketSaleInfo 的目标状态、审核说明、审核时间和审核人；状态为 PUBLISHED 时移除该集市组 override，其他状态设置 override=0；仅当状态在 PUBLISHED 与非 PUBLISHED 之间切换时触发 ACL 重算。
                    - 失败：当前身份不是平台管理员 -> PermissionError.UNAUTHORIZED；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；集市销售信息不存在 -> ResourceError.MARKET_SALE_INFO_NOT_FOUND；驳回或封禁未填写审核说明 -> ResourceError.MARKET_AUDIT_MESSAGE_INVALID；审核版本与当前提交版本不一致 -> ResourceError.MARKET_AUDIT_VERSION_CONFLICT。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "审核资源", businessType = BusinessType.UPDATE)
    @PostMapping("/auditSale")
    public R<Void> auditSale(@Valid @RequestBody MarketSaleAuditRequest request) {
        marketService.auditSaleInfo(request, SecurityContextHolder.getUserId().toString());
        return R.ok();
    }
}
