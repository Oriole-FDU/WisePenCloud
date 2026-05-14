package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.resource.domain.dto.req.SearchQueryRequest;
import com.oriole.wisepen.resource.domain.dto.res.SearchHitItemResponse;

/**
 * 搜索查询服务。
 * <p>
 * 上下文（userId、groupIds）由 {@link com.oriole.wisepen.common.core.context.SecurityContextHolder}
 * 在 service 内部读取，调用方无需透传，避免 Controller 漏传导致越权查询。
 */
public interface ISearchQueryService {

    /**
     * 执行全局搜索，按 {@link com.oriole.wisepen.resource.enums.SearchScope} 进行类型过滤，
     * 并按资源域位掩码 ACL 模型在 ES 侧完成可见性过滤：白名单（owner / DISCOVER 用户）
     * + nested groupAcls（baseDiscover 默认成员路径 + admin/owner 短路）。
     */
    PageResult<SearchHitItemResponse> globalSearch(SearchQueryRequest req);
}
