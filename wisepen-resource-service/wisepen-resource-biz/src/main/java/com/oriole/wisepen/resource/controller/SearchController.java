package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.res.MarketSearchHitItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.SearchHitItemResponse;
import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.SearchScope;
import com.oriole.wisepen.resource.service.ISearchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 全文搜索 Controller
 */
@Tag(name = "全文搜索", description = "跨资源类型的全文搜索（带 ACL 可见性过滤 + 高亮）")
@RestController
@RequestMapping("/resource/search")
@RequiredArgsConstructor
@CheckRole
public class SearchController {

    private final ISearchQueryService searchQueryService;

    @Operation(
            summary = "全文搜索资源",
            description = """
                    - 用途：在当前用户可见范围内跨资源类型检索资源内容和元信息。
                    - 请求：keyword 为搜索关键字，可为空；scope 指定搜索范围；page 和 size 控制分页。
                    - 约束：当前用户必须已登录；scope 必须是合法枚举。
                    - 处理：使用当前用户 ID 和小组角色上下文执行全文搜索，并应用资源可见性过滤与高亮处理；不返回当前用户无权查看的资源。
                    - 失败：搜索服务执行失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：返回分页搜索命中列表和总数。
                    """
    )
    @Log(title = "全文搜索", businessType = BusinessType.SELECT, isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/globalSearchResources")
    public R<PageR<SearchHitItemResponse>> searchResources(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "scope") SearchScope scope,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String userId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoleMap = SecurityContextHolder.getGroupRoleMap();

        return R.ok(searchQueryService.globalSearch(
                userId,
                groupRoleMap,
                keyword,
                scope,
                page,
                size
        ));
    }

    @Operation(
            summary = "全文搜索集市资源",
            description = """
                    - 用途：搜索集市中已发布的售卖资源快照，用于买家发现可购买资源。
                    - 请求：keyword 为搜索关键字，可为空；marketGroupId 可选，用于限定集市组；scope 指定搜索范围；page 和 size 控制分页。
                    - 约束：当前用户必须已登录；scope 必须是合法枚举；仅返回已发布的集市售卖快照。
                    - 处理：查询集市专用搜索索引，检索资源名、可预览范围内的 previewContent 和内部 searchContent；仅对资源名和 previewContent 做高亮；不复用普通资源 ACL；不返回 ES 技术主键和 searchContent。
                    - 失败：搜索服务执行失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：返回分页搜索命中列表和总数，命中项包含 resourceId、marketGroupId、resourceType、resourceName、ownerId、marketSaleInfo、highlightContent 和 updateTime。
                    """
    )
    @Log(title = "全文搜索集市资源", businessType = BusinessType.SELECT, isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/searchMarketResources")
    public R<PageR<MarketSearchHitItemResponse>> searchMarketResources(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "marketGroupId", required = false) String marketGroupId,
            @RequestParam(value = "scope") SearchScope scope,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(searchQueryService.marketSearch(
                keyword,
                marketGroupId,
                scope,
                MarketSaleStatus.PUBLISHED,
                page,
                size
        ));
    }
}
