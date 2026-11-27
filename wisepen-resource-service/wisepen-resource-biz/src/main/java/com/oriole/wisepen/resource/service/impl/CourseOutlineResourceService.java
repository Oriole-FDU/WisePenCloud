package com.oriole.wisepen.resource.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.dto.res.CourseOutlineResourceResponse;
import com.oriole.wisepen.resource.domain.dto.res.CourseResourceSearchResponse;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.GroupResConfigRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.repository.ResourceUserInteractionRecordRepository;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOutlineResourceService {
    private static final String COURSE_SCHEMA = "wisepen.course.v1";
    private static final String CONTENT_MATCH = "course_content";
    private static final String BINDS = "courseGroupBinds";
    private static final int MAX_KEYWORD_LENGTH = 200;
    private static final int MAX_CURSOR_LENGTH = 512;
    private static final IndexCoordinates INDEX = IndexCoordinates.of(SearchConstants.RESOURCE_INDEX_NAME);

    private final RemoteUserService remoteUserService;
    private final ObjectMapper objectMapper;
    private final TagRepository tagRepository;
    private final GroupResConfigRepository groupResConfigRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final ResourceUserInteractionRecordRepository interactionRepository;
    private final ResourceServiceImpl resourceService;
    private final ElasticsearchOperations elasticsearchOperations;

    public CourseResourceSearchResponse search(Long groupId, String userId, GroupRoleType role,
                                               String keyword, String cursor, int size) {
        String queryText = keyword.strip();
        if (queryText.isEmpty() || queryText.length() > MAX_KEYWORD_LENGTH || size < 1 || size > 100) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID);
        }
        String scope = DigestUtils.md5DigestAsHex(
                (groupId + "\n" + userId + "\n" + queryText).getBytes(StandardCharsets.UTF_8));
        List<Object> after = decodeCursor(cursor, scope);
        Outline outline = loadOutline(groupId);
        if (outline.chapters().isEmpty()) return new CourseResourceSearchResponse(List.of(), null);

        String courseId = groupId.toString();
        List<FieldValue> chapterIds = outline.chapters().stream().map(FieldValue::of).toList();
        Query placement = inCourse(courseId, Query.of(q -> q.terms(t -> t.field(BINDS + ".tagIds")
                .terms(v -> v.value(chapterIds)))));
        Query discover = indexedGrant(courseId, userId, role, "Discover");
        Query view = indexedGrant(courseId, userId, role, "View");
        Query title = Query.of(q -> q.match(m -> m.field("resourceName").query(queryText).boost(2.0f)));
        Query body = Query.of(q -> q.bool(b -> b.queryName(CONTENT_MATCH).filter(view)
                .must(m -> m.match(t -> t.field("content").query(queryText)))));
        Query match = Query.of(q -> q.bool(b -> b.filter(placement, discover)
                .should(title, body).minimumShouldMatch("1")
                .mustNot(q1 -> q1.term(t -> t.field("deleted").value(true)))));

        int scanLimit = size * 5;
        var builder = NativeQuery.builder().withQuery(match)
                .withPageable(PageRequest.ofSize(scanLimit + 1))
                .withTrackTotalHits(false)
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                .withSort(s -> s.field(field -> field.field("resourceId").order(SortOrder.Asc)))
                .withSourceFilter(new FetchSourceFilter(new String[]{"resourceId"}, null))
                .withHighlightQuery(SearchQueryServiceImpl.buildHighlightQuery(ESIndexEntity.class, "content"));
        if (!after.isEmpty()) builder.withSearchAfter(after);

        List<SearchHit<ESIndexEntity>> hits;
        try {
            hits = elasticsearchOperations.search(builder.build(), ESIndexEntity.class, INDEX).getSearchHits();
        } catch (RuntimeException e) {
            log.error("course resource search failed. groupId={}", groupId, e);
            throw new ServiceException(ResourceError.RESOURCE_SEARCH_FAILED);
        }
        int candidateCount = Math.min(hits.size(), scanLimit);
        List<String> candidateIds = hits.subList(0, candidateCount).stream().map(SearchHit::getId).toList();
        Map<String, ResourceItemEntity> current = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            resourceItemRepository.findAllById(candidateIds).forEach(resource -> current.put(resource.getResourceId(), resource));
        }

        List<CourseResourceSearchResponse.Hit> result = new ArrayList<>();
        int checked = 0;
        while (checked < candidateCount && result.size() < size) {
            SearchHit<ESIndexEntity> hit = hits.get(checked++);
            ResourceItemEntity resource = current.get(hit.getId());
            GroupTagBind bind = currentPlacement(resource, courseId);
            if (bind == null) continue;
            List<String> tagIds = bind.getTagIds().stream().filter(outline.chapters()::contains).distinct().toList();
            if (tagIds.isEmpty()) continue;
            int actions = resourceService.getCourseResourceActions(resource, bind, userId, role,
                    outline.defaultActions(), outline.tags());
            if (!ResourceAction.hasAction(actions, ResourceAction.DISCOVER)) continue;
            boolean canView = ResourceAction.hasAction(actions, ResourceAction.VIEW);
            boolean matchesContent = hit.getMatchedQueries().contains(CONTENT_MATCH);
            if (matchesContent && !canView) continue;
            String snippet = matchesContent ? SearchQueryServiceImpl.getHighlightText(hit, "content", null) : null;
            result.add(new CourseResourceSearchResponse.Hit(resource.getResourceId(), resource.getResourceType(),
                    resource.getResourceName(), tagIds, canView, snippet));
        }

        String next = checked > 0 && checked < hits.size()
                ? encodeCursor(scope, hits.get(checked - 1).getSortValues()) : null;
        return new CourseResourceSearchResponse(result, next);
    }

    public CourseOutlineResourceResponse resolve(Long groupId, String userId, GroupRoleType role,
                                                 String tagId, String resourceId) {
        if (!StringUtils.hasText(tagId) || !StringUtils.hasText(resourceId)) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID);
        }
        Outline outline = loadOutline(groupId);
        if (!outline.chapters().contains(tagId)) throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        ResourceItemEntity resource = resourceItemRepository.findById(resourceId).orElse(null);
        GroupTagBind bind = currentPlacement(resource, groupId.toString());
        if (bind == null || !bind.getTagIds().contains(tagId)) {
            throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        }
        int actions = resourceService.getCourseResourceActions(resource, bind, userId, role,
                outline.defaultActions(), outline.tags());
        if (!ResourceAction.hasAction(actions, ResourceAction.DISCOVER)) {
            throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        }
        if (!ResourceAction.hasAction(actions, ResourceAction.VIEW)) {
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        boolean read = interactionRepository.findByUserIdAndResourceId(userId, resourceId)
                .map(record -> Boolean.TRUE.equals(record.getRead())).orElse(false);
        return new CourseOutlineResourceResponse(tagId, resourceId, resource.getResourceType(),
                resource.getResourceName(), read);
    }

    private Outline loadOutline(Long groupId) {
        var response = remoteUserService.getGroupDisplayInfo(List.of(groupId));
        Map<Long, GroupDisplayBase> groups = response.getData();
        if (!Integer.valueOf(200).equals(response.getCode()) || groups == null) {
            throw new ServiceException(ResourceError.RESOURCE_SEARCH_FAILED);
        }
        GroupDisplayBase group = groups.get(groupId);
        if (group == null || group.getGroupType() != GroupType.ADVANCED_GROUP) {
            throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
        }
        if (!StringUtils.hasText(group.getGroupMetaInfo())) return Outline.empty();
        JsonNode course;
        try {
            course = objectMapper.readTree(group.getGroupMetaInfo()).path("course");
        } catch (JsonProcessingException e) {
            throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
        }
        if (!COURSE_SCHEMA.equals(course.path("schema").asText())) return Outline.empty();
        String rootId = course.path("outlineRootTagId").asText("");
        if (rootId.isBlank()) return Outline.empty();

        String courseId = groupId.toString();
        Map<String, TagEntity> tags = new HashMap<>();
        Map<String, List<TagEntity>> childrenByParent = new HashMap<>();
        for (TagEntity tag : tagRepository.findByGroupId(courseId)) {
            tags.put(tag.getTagId(), tag);
            childrenByParent.computeIfAbsent(tag.getParentId(), ignored -> new ArrayList<>()).add(tag);
        }
        TagEntity root = tags.get(rootId);
        Set<String> visited = new HashSet<>();
        String ancestorId = rootId;
        while (!"0".equals(ancestorId)) {
            TagEntity tag = tags.get(ancestorId);
            if (tag == null || !visited.add(ancestorId) || hidden(tag)
                    || ("0".equals(tag.getParentId()) && tag.getTagName().startsWith("/"))) {
                throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
            }
            ancestorId = tag.getParentId();
        }

        Set<String> chapters = new HashSet<>();
        var pending = new ArrayDeque<>(childrenByParent.getOrDefault(root.getTagId(), List.of()));
        while (!pending.isEmpty()) {
            TagEntity tag = pending.removeFirst();
            if (!hidden(tag) && !rootId.equals(tag.getTagId()) && chapters.add(tag.getTagId())) {
                pending.addAll(childrenByParent.getOrDefault(tag.getTagId(), List.of()));
            }
        }
        int defaults = groupResConfigRepository.findByGroupId(courseId)
                .map(GroupResConfigEntity::getDefaultMemberActionsMask)
                .orElse(ResourceAction.DEFAULT_MEMBER_ACTIONS);
        return new Outline(tags, chapters, defaults);
    }

    private static boolean hidden(TagEntity tag) {
        return tag.getTagName() == null || tag.getTagName().strip().startsWith(".");
    }

    private static GroupTagBind currentPlacement(ResourceItemEntity resource, String groupId) {
        if (resource == null || resource.getDeletedAt() != null || resource.getGroupBinds() == null) return null;
        return resource.getGroupBinds().stream()
                .filter(bind -> groupId.equals(bind.getGroupId()) && ESIndexEntity.isGroupSearchPlacement(bind))
                .findFirst().orElse(null);
    }

    private static Query indexedGrant(String groupId, String userId, GroupRoleType role, String action) {
        Query owner = Query.of(q -> q.term(t -> t.field("ownerId").value(userId)));
        Query specified = Query.of(q -> q.term(t -> t.field("course" + action + "Users").value(userId)));
        Query specifiedOverride = Query.of(q -> q.term(t -> t.field("courseSpecifiedUsers").value(userId)));
        Query otherwise = Query.of(q -> q.matchAll(m -> m));
        if (role == GroupRoleType.MEMBER) {
            Query defaultGrant = Query.of(q -> q.term(t -> t.field(BINDS + ".can" + action).value(true)));
            Query exception = Query.of(q -> q.term(t -> t.field(BINDS + ".except" + action + "Users").value(userId)));
            Query effectiveGrant = Query.of(q -> q.bool(b -> b.minimumShouldMatch("1")
                    .should(a -> a.bool(allow -> allow.filter(defaultGrant).mustNot(exception)))
                    .should(a -> a.bool(allow -> allow.filter(exception).mustNot(defaultGrant)))));
            otherwise = inCourse(groupId, effectiveGrant);
        }
        Query groupGrant = otherwise;
        return Query.of(q -> q.bool(b -> b.minimumShouldMatch("1").should(owner, specified)
                .should(a -> a.bool(allow -> allow.filter(groupGrant).mustNot(specifiedOverride)))));
    }

    private static Query inCourse(String groupId, Query filter) {
        return Query.of(q -> q.nested(n -> n.path(BINDS).scoreMode(ChildScoreMode.None)
                .query(nested -> nested.bool(b -> b.filter(filter)
                        .filter(f -> f.term(t -> t.field(BINDS + ".groupId").value(groupId)))))));
    }

    private static List<Object> decodeCursor(String cursor, String scope) {
        if (cursor == null) return List.of();
        try {
            if (cursor.length() > MAX_CURSOR_LENGTH) throw new IllegalArgumentException();
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\n", 3);
            if (parts.length != 3 || !scope.equals(parts[0]) || !StringUtils.hasText(parts[2])) {
                throw new IllegalArgumentException();
            }
            double score = Double.parseDouble(parts[1]);
            if (!Double.isFinite(score) || score < 0) throw new IllegalArgumentException();
            return List.of(score, parts[2]);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID);
        }
    }

    private static String encodeCursor(String scope, List<Object> sort) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (scope + "\n" + sort.get(0) + "\n" + sort.get(1)).getBytes(StandardCharsets.UTF_8));
    }

    private record Outline(Map<String, TagEntity> tags, Set<String> chapters, int defaultActions) {
        private static Outline empty() { return new Outline(Map.of(), Set.of(), 0); }
    }
}
