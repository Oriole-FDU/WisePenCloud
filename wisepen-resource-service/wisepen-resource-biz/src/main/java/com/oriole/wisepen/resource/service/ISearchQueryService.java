package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.domain.dto.res.MarketSearchHitItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.SearchHitItemResponse;
import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.enums.SearchScope;

import java.util.List;
import java.util.Map;

/**
 * 全文搜索查询服务
 */
public interface ISearchQueryService {

    /**
     * 全局全文搜索入口
     */
    PageR<SearchHitItemResponse> globalSearch(String currentUserId, Map<Long, GroupRoleType>  groupRoleMap,
                                              String keyword, SearchScope scope, List<ResourceType> resourceTypes,
                                              int page, int size);

    PageR<MarketSearchHitItemResponse> marketSearch(String keyword, String marketGroupId, SearchScope scope, MarketSaleStatus marketSaleStatus, int page, int size);

}
