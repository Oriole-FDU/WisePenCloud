package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.req.MarketSalePublishRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleOffShelfRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketPurchaseRequest;
import com.oriole.wisepen.resource.domain.dto.res.MarketOrderResponse;
import com.oriole.wisepen.resource.service.IMarketService;
import com.oriole.wisepen.resource.service.IResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "集市", description = "资源上架、下架、购买和购买记录查询")
@RestController
@RequestMapping("/resource/market")
@RequiredArgsConstructor
@CheckRole
@Validated
public class MarketController {

    private final IMarketService marketService;
    private final IResourceService resourceService;


    @Operation(
            summary = "提交上架信息",
            description = """
                    - 用途：资源所有者在集市组中提交或更新资源的集市销售信息。
                    - 请求：resourceId 指定资源；marketGroupId 使用原始集市组ID；tagIds 指定该集市组下的标签；offerVersion 指定本次提交对应的资源版本；reviewContentPercentage 与 reviewActions 指定预览范围和预览动作；marketSaleTiers 指定售卖档位、授权动作和价格。
                    - 约束：当前用户必须是资源所有者；marketGroupId 必须对应 MARKET_GROUP；tagIds 必须属于该集市组标签空间；已封禁的集市销售信息不能再次提交；预览动作和售卖档位动作不能包含 EDIT；售卖档位授权动作不能重复。
                    - 处理：覆盖该集市组绑定的 tagIds 和整组 marketSaleInfo；已发布或已下架且 offerVersion 未变化时直接置为 PUBLISHED 并移除该集市组 override；其他提交进入 PENDING_REVIEW，清空审核信息并设置该集市组 override=0；提交后为 PUBLISHED 时触发 ACL 重算。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；集市组不存在或不是 MARKET_GROUP -> ResourceError.MARKET_GROUP_NOT_FOUND；标签不存在或不属于该集市组标签空间 -> ResourceError.TAG_NODE_NOT_FOUND；已封禁的集市销售信息再次提交 -> ResourceError.CANNOT_REPUBLISH_BANNED_MARKET_SALE；集市权限包含禁止动作 -> ResourceError.MARKET_ACTIONS_INVALID；售卖档位授权动作重复 -> ResourceError.MARKET_SALE_TIER_ACTIONS_DUPLICATED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "提交上架信息", businessType = BusinessType.INSERT)
    @PostMapping("/publishSaleInfo")
    public R<Void> publishSaleInfo(@Valid @RequestBody MarketSalePublishRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();

        resourceService.assertResourceOwner(request.getResourceId(), userId);
        marketService.publishSaleInfo(request);
        return R.ok();
    }

    @Operation(
            summary = "下架资源",
            description = """
                    - 用途：资源所有者将已经发布的集市销售信息下架。
                    - 请求：resourceId 指定资源；marketGroupId 使用原始集市组ID，定位该资源在目标集市组中的销售信息。
                    - 约束：当前用户必须是资源所有者；目标集市销售信息必须存在且状态为 PUBLISHED；已封禁、待审核、已驳回或已下架的销售信息不能通过该接口下架。
                    - 处理：将整组 marketSaleInfo 状态置为 OFF_SHELF，设置该集市组 override=0 并触发 ACL 重算；不删除售卖档位、购买记录或已写入的购买授权。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；当前用户不是资源所有者 -> ResourceError.RESOURCE_PERMISSION_DENIED；集市销售信息不存在 -> ResourceError.MARKET_SALE_INFO_NOT_FOUND；已封禁的集市销售信息不可操作 -> ResourceError.CANNOT_REPUBLISH_BANNED_MARKET_SALE；集市销售信息未上架或已下架 -> ResourceError.CANNOT_OPERATE_OFF_SHELF_MARKET_SALE。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "下架资源", businessType = BusinessType.UPDATE)
    @PostMapping("/offShelfSaleInfo")
    public R<Void> offShelfSaleInfo(@Valid @RequestBody MarketSaleOffShelfRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();

        resourceService.assertResourceOwner(request.getResourceId(), userId);
        marketService.offShelfSaleInfo(request);
        return R.ok();
    }

    @Operation(
            summary = "购买资源",
            description = """
                    - 用途：买家购买集市资源的某个售卖档位授权。
                    - 请求：resourceId 指定资源；marketGroupId 使用原始集市组ID，定位该资源在目标集市组中的销售信息；offerId 指定要购买的售卖档位。
                    - 约束：集市销售信息必须存在且状态为 PUBLISHED；买家不能购买自己上架的资源；offerId 必须命中当前售卖档位；买家尚未完整拥有该档位授权。
                    - 处理：按 offerId 选中售卖档位并请求钱包结算；创建购买订单，记录 traceId、购买版本、授权动作和支付价格；将售卖档位授权按位或写入 marketSpecifiedUsersGrantedActionsMask 并触发 ACL 重算。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；集市销售信息不存在 -> ResourceError.MARKET_SALE_INFO_NOT_FOUND；集市销售信息未上架 -> ResourceError.CANNOT_PURCHASE_OFF_SHELF_MARKET_SALE；购买自己上架的资源 -> ResourceError.CANNOT_PURCHASE_OWN_MARKET_SALE；售卖档位不存在 -> ResourceError.MARKET_SALE_TIER_NOT_FOUND；已拥有该售卖档位授权 -> ResourceError.MARKET_SALE_TIER_GRANT_ALREADY_EXISTS。
                    - 响应：返回本次购买记录信息。
                    """
    )
    @Log(title = "购买资源", businessType = BusinessType.INSERT)
    @PostMapping("/purchaseResource")
    public R<MarketOrderResponse> purchaseResource(@Valid @RequestBody MarketPurchaseRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();

        return R.ok(marketService.purchaseResource(request, userId));
    }

    @Operation(
            summary = "分页查询我的购买",
            description = """
                    - 用途：查询当前用户购买过的集市资源订单，供用户查看已购买授权、支付价格和购买版本。
                    - 请求：page、size 控制分页。
                    - 约束：当前用户必须已登录。
                    - 处理：按当前用户 buyerId 分页查询订单；不校验原资源当前是否仍存在、上架或可访问。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回当前用户购买记录分页。
                    """
    )
    @GetMapping("/listOrders")
    public R<PageR<MarketOrderResponse>> listOrders(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) int size) {
        return R.ok(marketService.listOrders(SecurityContextHolder.getUserId().toString(), page, size));
    }
}
