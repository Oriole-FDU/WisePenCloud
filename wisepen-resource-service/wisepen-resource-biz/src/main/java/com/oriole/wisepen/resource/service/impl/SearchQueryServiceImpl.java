package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.dto.req.SearchQueryRequest;
import com.oriole.wisepen.resource.domain.dto.res.SearchHitItemResponse;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.enums.SearchScope;
import com.oriole.wisepen.resource.exception.SearchErrorCode;
import com.oriole.wisepen.resource.service.ISearchQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 全局搜索服务实现。
 *
 * <h3>ACL 过滤 — 对齐 {@code listResources} 4 种情况 + admin/owner 短路</h3>
 * 构造 3 个 should 分支（OR）：
 * <ol>
 *   <li>{@code term allowedUsers = userId}：覆盖情况 A（owner）+ B（resourceLevel DISCOVER）
 *       + C（userMasks DISCOVER）</li>
 *   <li>nested {@code groupAcls}：用户是成员组 + baseDiscover=true + 不在 explicitlyDeniedUsers
 *       → 情况 D（默认掩码可见且未被 BLACKLIST）</li>
 *   <li>nested {@code groupAcls}：groupAcls.groupId 命中用户的 ADMIN/OWNER 组列表
 *       → admin/owner 跳过所有 ACL（与 listResources 中 {@code userGroupRole == ADMIN || OWNER}
 *       的整段跳过 ACL 行为对齐）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchQueryServiceImpl implements ISearchQueryService {



    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public PageResult<SearchHitItemResponse> globalSearch(SearchQueryRequest req) {
        long start = System.currentTimeMillis();
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            // Controller 上的 @CheckLogin 已经拦截匿名访问，这里再兜一道防 NPE
            throw new ServiceException(SearchErrorCode.ES_QUERY_BUILD_ERROR);
        }
        String userIdStr = String.valueOf(userId);
        Map<Long, GroupRoleType> groupRoleMap = SecurityContextHolder.getGroupRoleMap();
        List<String> memberGroupIds = groupRoleMap.keySet().stream()
                .map(String::valueOf)
                .toList();
        List<String> adminOwnerGroupIds = groupRoleMap.entrySet().stream()
                .filter(e -> e.getValue() == GroupRoleType.ADMIN || e.getValue() == GroupRoleType.OWNER)
                .map(e -> String.valueOf(e.getKey()))
                .toList();

        SearchScope scope = req.getScope() == null ? SearchScope.ALL : req.getScope();

        BoolQuery.Builder boolBuilder = QueryBuilders.bool();

        boolBuilder.must(QueryBuilders.multiMatch(m -> m
                .query(req.getKeyword())
                .fields(SearchConstants.BOOSTED_SEARCH_FIELDS)));

        boolBuilder.filter(f -> f.bool(b -> buildAclShould(b, userIdStr, memberGroupIds, adminOwnerGroupIds)));

        // SearchScope 三分支过滤：ALL 不加类型过滤；DOCUMENT / NOTE 一次 terms 命中对应集合，讲白了分区搜索快一些
        if (scope != SearchScope.ALL) {
            List<FieldValue> typeValues = scope.includedResourceTypes().stream()
                    .map(ResourceType::getExtension)
                    .map(FieldValue::of)
                    .toList();
            boolBuilder.filter(f -> f.terms(t -> t.field(SearchConstants.FIELD_RESOURCE_TYPE).terms(ts -> ts.value(typeValues))));
        }

        NativeQuery query = buildNativeQuery(boolBuilder.build()._toQuery(), req);
        SearchHits<ESIndexEntity> searchHits = elasticsearchOperations.search(query, ESIndexEntity.class);

        List<SearchHitItemResponse> items = searchHits.stream().map(this::toResDTO).toList();
        PageResult<SearchHitItemResponse> result = new PageResult<>(
                searchHits.getTotalHits(), req.getPage(), req.getSize());
        result.addAll(items);

        log.info("search done keyword={} scope={} userId={} memberGroups={} adminGroups={} total={} hits={} costMs={}",
                req.getKeyword(), scope, userId, memberGroupIds.size(), adminOwnerGroupIds.size(),
                searchHits.getTotalHits(), items.size(), System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 构建 ACL 的 should（OR）三分支。
     */
    private BoolQuery.Builder buildAclShould(BoolQuery.Builder b,
                                             String userIdStr,
                                             List<String> memberGroupIds,
                                             List<String> adminOwnerGroupIds) {
        // 分支 1：白名单用户（owner / 资源级 DISCOVER / userMasks DISCOVER 已合并到 allowedUsers）
        b.should(s -> s.term(t -> t.field(SearchConstants.FIELD_ALLOWED_USERS).value(userIdStr)));

        // 分支 2：用户是成员组 + 该组 baseDiscover=true + 未被该组 BLACKLIST 否决
        if (CollUtil.isNotEmpty(memberGroupIds)) {
            List<FieldValue> memberValues = memberGroupIds.stream().map(FieldValue::of).toList();
            b.should(s -> s.nested(n -> n
                    .path(SearchConstants.NESTED_PATH_GROUP_ACLS)
                    .query(q -> q.bool(bb -> bb
                            .must(QueryBuilders.terms(t -> t
                                    .field(SearchConstants.FIELD_GROUP_ACLS_GROUP_ID)
                                    .terms(ts -> ts.value(memberValues))))
                            .must(QueryBuilders.term(t -> t
                                    .field(SearchConstants.FIELD_GROUP_ACLS_BASE_DISCOVER)
                                    .value(true)))
                            .mustNot(QueryBuilders.term(t -> t
                                    .field(SearchConstants.FIELD_GROUP_ACLS_DENIED_USERS)
                                    .value(userIdStr)))))
                    .scoreMode(ChildScoreMode.None)));
        }

        // 分支 3：admin/owner 短路 —— 只要资源绑定的任一组里用户身份是 ADMIN/OWNER，直接放行
        if (CollUtil.isNotEmpty(adminOwnerGroupIds)) {
            List<FieldValue> adminValues = adminOwnerGroupIds.stream().map(FieldValue::of).toList();
            b.should(s -> s.nested(n -> n
                    .path(SearchConstants.NESTED_PATH_GROUP_ACLS)
                    .query(q -> q.terms(t -> t
                            .field(SearchConstants.FIELD_GROUP_ACLS_GROUP_ID)
                            .terms(ts -> ts.value(adminValues))))
                    .scoreMode(ChildScoreMode.None)));
        }
        // minimum_should_match=1：任一 should 命中即可放行（filter 块默认满足该约束）
        return b;
    }

    private NativeQuery buildNativeQuery(Query query, SearchQueryRequest req) {
        HighlightParameters hlParams = HighlightParameters.builder()
                .withPreTags(SearchConstants.HIGHLIGHT_PRE_TAG)
                .withPostTags(SearchConstants.HIGHLIGHT_POST_TAG)
                .withFragmentSize(SearchConstants.HIGHLIGHT_FRAGMENT_SIZE)
                .withNumberOfFragments(SearchConstants.HIGHLIGHT_MAX_FRAGMENTS)
                .build();
        Highlight highlight = new Highlight(hlParams, List.of(
                new HighlightField(SearchConstants.FIELD_RESOURCE_NAME),
                new HighlightField(SearchConstants.FIELD_CONTENT)));

        return new NativeQueryBuilder()
                .withQuery(query)
                .withPageable(PageRequest.of(req.getPage() - 1, req.getSize()))
                .withHighlightQuery(new HighlightQuery(highlight, ESIndexEntity.class))
                .build();
    }

    private SearchHitItemResponse toResDTO(SearchHit<ESIndexEntity> hit) {
        ESIndexEntity entity = hit.getContent();

        String resourceName = hit.getHighlightField(SearchConstants.FIELD_RESOURCE_NAME).stream()
                .findFirst()
                .orElse(entity.getResourceName());

        // 正文高亮可能为空（用户搜的关键词只命中标题）：保持 null，前端按需做空判断
        List<String> contentHighlights = hit.getHighlightField(SearchConstants.FIELD_CONTENT);
        String highlightContent = CollUtil.isNotEmpty(contentHighlights)
                ? StrUtil.join(SearchConstants.HIGHLIGHT_FRAGMENT_SEPARATOR, contentHighlights)
                : null;

        return SearchHitItemResponse.builder()
                .resourceId(entity.getResourceId())
                .resourceType(ResourceType.fromExtension(entity.getResourceType()))
                .resourceName(resourceName)
                .highlightContent(highlightContent)
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
