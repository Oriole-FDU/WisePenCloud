package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.dto.res.MarketSearchHitItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.SearchHitItemResponse;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.entity.MarketESIndexEntity;
import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.enums.SearchScope;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.service.ISearchQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全文搜索查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryServiceImpl implements ISearchQueryService {

    private final ElasticsearchOperations elasticsearchOperations;

    private static final IndexCoordinates INDEX = IndexCoordinates.of(SearchConstants.RESOURCE_INDEX_NAME);
    private static final IndexCoordinates MARKET_INDEX = IndexCoordinates.of(SearchConstants.MARKET_RESOURCE_INDEX_NAME);

    @Override
    public PageR<SearchHitItemResponse> globalSearch(String currentUserId,
                                                     Map<Long, GroupRoleType>  groupRoleMap,
                                                     String keyword,
                                                     SearchScope scope,
                                                     List<ResourceType> resourceTypes,
                                                     int page, int size){
        // 构建 Query
        Query mainQuery = buildQuery(keyword, scope, resourceTypes, currentUserId, groupRoleMap);
        HighlightQuery highlightQuery = buildHighlightQuery(ESIndexEntity.class, "content");
        Pageable pageable = PageRequest.of(page - 1, size);

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(mainQuery).withHighlightQuery(highlightQuery).withPageable(pageable)
                .withTrackTotalHits(true);

        NativeQuery nativeQuery = builder.build();

        // 执行 Query
        SearchHits<ESIndexEntity> searchHits;
        try {
            searchHits = elasticsearchOperations.search(nativeQuery, ESIndexEntity.class, INDEX);
        } catch (Exception e) {
            log.error("search query failed. keyword={} scope={}", keyword, scope, e);
            throw new ServiceException(ResourceError.RESOURCE_SEARCH_FAILED);
        }

        long total = searchHits.getTotalHits();
        PageR<SearchHitItemResponse> result = new PageR<>(total, page, size);

        for (SearchHit<ESIndexEntity> hit : searchHits.getSearchHits()) {
            result.add(toResponse(hit));
        }

        log.debug("search query succeeded. keyword={} scope={} userId={} total={} page={} size={}",
                keyword, scope, currentUserId, total, page, size);
        return result;
    }

    @Override
    public PageR<MarketSearchHitItemResponse> marketSearch(String keyword,
                                                           String marketGroupId,
                                                           SearchScope scope,
                                                           MarketSaleStatus marketSaleStatus,
                                                           int page,
                                                           int size) {
        // 构建 Query
        Query mainQuery = buildMarketQuery(keyword, marketGroupId, scope, marketSaleStatus);
        HighlightQuery highlightQuery = buildHighlightQuery(MarketESIndexEntity.class, "previewContent");
        Pageable pageable = PageRequest.of(page - 1, size);

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(mainQuery)
                .withHighlightQuery(highlightQuery)
                .withPageable(pageable)
                .withTrackTotalHits(true);

        NativeQuery nativeQuery = builder.build();

        // 执行 Query
        SearchHits<MarketESIndexEntity> searchHits;
        try {
            searchHits = elasticsearchOperations.search(nativeQuery, MarketESIndexEntity.class, MARKET_INDEX);
        } catch (Exception e) {
            log.error("market search query failed. keyword={} marketGroupId={} scope={}", keyword, marketGroupId, scope, e);
            throw new ServiceException(ResourceError.RESOURCE_SEARCH_FAILED);
        }

        long total = searchHits.getTotalHits();
        PageR<MarketSearchHitItemResponse> result = new PageR<>(total, page, size);

        for (SearchHit<MarketESIndexEntity> hit : searchHits.getSearchHits()) {
            result.add(toMarketResponse(hit));
        }

        log.debug("market search query succeeded. keyword={} marketGroupId={} scope={} total={} page={} size={}",
                keyword, marketGroupId, scope, total, page, size);
        return result;
    }

    private Query buildQuery(String keyword,
                             SearchScope scope,
                             List<ResourceType> resourceTypes,
                             String userId,
                             Map<Long, GroupRoleType> groupRoleMap) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        // [must] 关键词召回
        bool.must(buildKeywordQuery(keyword, SearchConstants.BOOSTED_SEARCH_FIELDS));

        // [filter] SearchScope 类型过滤
        if (scope != null) {
            bool.filter(buildScopeFilter(scope));
        }
        if (resourceTypes != null && !resourceTypes.isEmpty()) {
            bool.filter(buildResourceTypesFilter(resourceTypes));
        }
        // [filter] ACL 可见性过滤
        bool.filter(buildAclFilter(userId, groupRoleMap));

        return Query.of(q -> q.bool(bool.build()));
    }

    private Query buildMarketQuery(String keyword, String marketGroupId, SearchScope scope, MarketSaleStatus marketSaleStatus) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // [must] 关键词召回
        bool.must(buildKeywordQuery(keyword, SearchConstants.MARKET_BOOSTED_SEARCH_FIELDS));

        // [filter] SearchScope 类型过滤
        if (scope != null) {
            bool.filter(buildScopeFilter(scope));
        }

        // [filter] Market 组过滤
        if (StringUtils.hasText(marketGroupId)) {
            bool.filter(Query.of(q -> q.term(t -> t.field("marketGroupId").value(marketGroupId))));
        }

        // [filter] Market 售卖状态过滤；管理员搜索可传 null 表示不限状态
        if (marketSaleStatus != null) {
            bool.filter(Query.of(q -> q.term(t -> t
                    .field("marketSaleStatus")
                    .value(marketSaleStatus.getValue())
            )));
        }

        return Query.of(q -> q.bool(bool.build()));
    }

    private Query buildKeywordQuery(String keyword, String[] fields) {
        return Query.of(q -> q.multiMatch(m -> m
                .query(keyword == null ? "" : keyword.trim())
                .fields(List.of(fields))
                .analyzer(SearchConstants.ANALYZER_IK_SMART)
        ));
    }

    private Query buildScopeFilter(SearchScope scope) {
        List<String> exts = scope.includedResourceTypes().stream().map(ResourceType::getExtension).toList();
        return buildResourceTypeExtensionsFilter(exts);
    }

    private Query buildResourceTypesFilter(List<ResourceType> resourceTypes) {
        List<String> exts = resourceTypes.stream().map(ResourceType::getExtension).toList();
        return buildResourceTypeExtensionsFilter(exts);
    }

    private Query buildResourceTypeExtensionsFilter(List<String> exts) {
        return Query.of(q -> q.terms(t -> t
                .field("resourceType")
                .terms(tv -> tv.value(exts.stream()
                        .map(FieldValue::of)
                        .toList()))));
    }

    private Query buildAclFilter(String userId, Map<Long, GroupRoleType> groupRoleMap) {
        BoolQuery.Builder aclFilter = new BoolQuery.Builder();
        aclFilter.minimumShouldMatch("1");

        // Case A: 自己的资源
        aclFilter.should(Query.of(q -> q.term(t -> t.field("ownerId").value(userId))));
        // Case B: 资源级 DISCOVER 特权
        aclFilter.should(Query.of(q -> q.term(t -> t.field("specifiedDiscoverUsers").value(userId))));
        // Case C + D + ADMIN/OWNER 短路：合并到一个 nested 查询
        Set<String> managedGroupId = groupRoleMap.entrySet().stream()
                .filter(e -> e.getValue() == GroupRoleType.ADMIN || e.getValue() == GroupRoleType.OWNER)
                .map(e -> e.getKey().toString()).collect(Collectors.toSet());
        Set<String> joinedGroupId = groupRoleMap.keySet().stream().map(String::valueOf).collect(Collectors.toSet());

        if (!joinedGroupId.isEmpty()) {
            BoolQuery.Builder nestedBool = new BoolQuery.Builder().minimumShouldMatch("1");

            // Group ADMIN/OWNER 直接获得小组资源的检索权限
            if (!managedGroupId.isEmpty()) {
                nestedBool.should(Query.of(q -> q.terms(t -> t
                        .field("computedGroupAcls.groupId")
                        .terms(tv -> tv.value(managedGroupId.stream().map(FieldValue::of).toList())))));
            }

            // Group MEMBER
            // 在预计算权限可发现且不在黑名单（specifiedUsers）中，获得资源的检索权限
            nestedBool.should(Query.of(q -> q.bool(b -> b
                    .must(Query.of(q1 -> q1.terms(t -> t
                            .field("computedGroupAcls.groupId")
                            .terms(tv -> tv.value(joinedGroupId.stream().map(FieldValue::of).toList())))))
                    .must(Query.of(q2 -> q2.term(t -> t
                            .field("computedGroupAcls.isDiscover").value(true))))
                    .mustNot(Query.of(q3 -> q3.term(t -> t
                            .field("computedGroupAcls.specifiedUsers").value(userId))))
            )));
            // 在预计算权限不可发现但在白名单（specifiedUsers）中，获得资源的检索权限
            nestedBool.should(Query.of(q -> q.bool(b -> b
                    .must(Query.of(q1 -> q1.terms(t -> t
                            .field("computedGroupAcls.groupId")
                            .terms(tv -> tv.value(joinedGroupId.stream().map(FieldValue::of).toList())))))
                    .must(Query.of(q2 -> q2.term(t -> t
                            .field("computedGroupAcls.isDiscover").value(false))))
                    .must(Query.of(q3 -> q3.term(t -> t
                            .field("computedGroupAcls.specifiedUsers").value(userId))))
            )));

            Query nestedBoolQuery = Query.of(q -> q.bool(nestedBool.build()));
            aclFilter.should(Query.of(q -> q.nested(n -> n
                    .path("computedGroupAcls").scoreMode(ChildScoreMode.None).query(nestedBoolQuery))));
        }
        return Query.of(q -> q.bool(aclFilter.build()));
    }

    private HighlightQuery buildHighlightQuery(Class<?> entityClass, String contentField) {
        // 配置为返回若干命中的片段
        HighlightFieldParameters fieldParams = HighlightFieldParameters.builder()
                .withFragmentSize(SearchConstants.HIGHLIGHT_FRAGMENT_SIZE)
                .withNumberOfFragments(SearchConstants.HIGHLIGHT_MAX_FRAGMENTS)
                .build();

        // 高亮字段
        HighlightField nameField = new HighlightField("resourceName", fieldParams);
        HighlightField contentHighlightField = new HighlightField(contentField, fieldParams);

        // 高亮包裹
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags(SearchConstants.HIGHLIGHT_PRE_TAG)
                .withPostTags(SearchConstants.HIGHLIGHT_POST_TAG)
                .build();

        Highlight highlight = new Highlight(params, List.of(nameField, contentHighlightField));
        return new HighlightQuery(highlight, entityClass);
    }

    private SearchHitItemResponse toResponse(SearchHit<ESIndexEntity> hit) {
        ESIndexEntity content = hit.getContent();

        SearchHitItemResponse res = BeanUtil.copyProperties(content, SearchHitItemResponse.class);
        res.setResourceType(ResourceType.fromExtension(content.getResourceType()));
        // 高亮覆写 resourceName
        res.setResourceName(getHighlightText(hit, "resourceName", content.getResourceName()));
        // 高亮覆写 content（无高亮返回空）
        res.setHighlightContent(getHighlightText(hit, "content", null));
        return res;
    }

    private MarketSearchHitItemResponse toMarketResponse(SearchHit<MarketESIndexEntity> hit) {
        MarketESIndexEntity content = hit.getContent();

        MarketSearchHitItemResponse res = BeanUtil.copyProperties(content, MarketSearchHitItemResponse.class);
        res.setResourceType(ResourceType.fromExtension(content.getResourceType()));
        // 高亮覆写 resourceName
        res.setResourceName(getHighlightText(hit, "resourceName", content.getResourceName()));
        // 高亮覆写 content（无高亮返回空）
        res.setHighlightContent(getHighlightText(hit, "previewContent", null));
        return res;
    }

    private String getHighlightText(SearchHit<?> hit, String fieldName, String fallback) {
        List<String> fragments = hit.getHighlightFields().get(fieldName);
        return fragments != null && !fragments.isEmpty() ?
                fragments.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(SearchConstants.HIGHLIGHT_FRAGMENT_SEPARATOR)) : fallback;
    }
}
