package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.ComputedGroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = SearchConstants.RESOURCE_INDEX_NAME, createIndex = true)
public class ESIndexEntity {

    @Id
    @Field(type = FieldType.Keyword)
    private String resourceId;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String resourceName;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String content;

    @Field(type = FieldType.Keyword)
    private String ownerId;

    @Field(type = FieldType.Keyword)
    private List<String> specifiedDiscoverUsers;

    @Field(type = FieldType.Nested)
    private List<ComputedGroupAclProjection> computedGroupAcls;

    @Field(type = FieldType.Keyword)
    private List<String> courseSpecifiedUsers;

    @Field(type = FieldType.Keyword)
    private List<String> courseDiscoverUsers;

    @Field(type = FieldType.Keyword)
    private List<String> courseViewUsers;

    @Field(type = FieldType.Nested)
    private List<CourseGroupBindProjection> courseGroupBinds;

    @Field(type = FieldType.Long)
    private Long courseProjectionVersion;

    @Field(type = FieldType.Boolean)
    private Boolean deleted;

    public record CourseGroupBindProjection(
            @Field(type = FieldType.Keyword) String groupId,
            @Field(type = FieldType.Keyword) List<String> tagIds,
            @Field(type = FieldType.Boolean) boolean canDiscover,
            @Field(type = FieldType.Keyword) List<String> exceptDiscoverUsers,
            @Field(type = FieldType.Boolean) boolean canView,
            @Field(type = FieldType.Keyword) List<String> exceptViewUsers) {}

    @Field(type = FieldType.Date,
            format = {},
            pattern = SearchConstants.ES_DATE_FORMAT_PATTERN)
    private LocalDateTime updateTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComputedGroupAclProjection {

        @Field(type = FieldType.Keyword)
        private String groupId;

        @Field(type = FieldType.Boolean)
        private Boolean isDiscover;

        @Field(type = FieldType.Keyword)
        @Builder.Default
        private List<String> specifiedUsers = new ArrayList<>();
    }

    public void setResourceName(String resourceName) {
        this.resourceName = HtmlUtils.htmlEscape(resourceName);
    }

    public void setContent(String content) {
        this.content = HtmlUtils.htmlEscape(content);
    }

    public ESIndexEntity(String resourceId, String content) {
        this.resourceId = resourceId;
        this.content = HtmlUtils.htmlEscape(content);
        this.updateTime = LocalDateTime.now();
    }

    public ESIndexEntity(ResourceItemEntity entity) {
        this.resourceId = entity.getResourceId();
        this.resourceType = entity.getResourceType() != null ? entity.getResourceType().getExtension() : null;
        this.resourceName = HtmlUtils.htmlEscape(entity.getResourceName());
        this.ownerId = entity.getOwnerId();
        this.updateTime = entity.getUpdateTime() != null ? entity.getUpdateTime() : LocalDateTime.now();

        Set<String> marketGroupIds = entity.getGroupBinds() == null ? Collections.emptySet() :
                entity.getGroupBinds().stream()
                        .filter(bind -> bind.getMarketSaleInfo() != null)
                        .map(bind -> bind.getGroupId())
                        .collect(Collectors.toSet());

        // 组 DISCOVER 权限
        this.computedGroupAcls = entity.getComputedGroupAcls() != null ?
                entity.getComputedGroupAcls().entrySet().stream()
                        .filter(entry -> !marketGroupIds.contains(entry.getKey())) // Market组不写入ACL以避免从普通搜索中获取到信息
                        .map(entry -> {
                            boolean isDiscover = ResourceAction.permissionCodeToActions(entry.getValue().getBaseMask()).contains(ResourceAction.DISCOVER);
                            List<String> specifiedUsers = entry.getValue().getUserMasks().entrySet().stream().filter(
                                    userMask -> ResourceAction.hasAction(userMask.getValue(), ResourceAction.DISCOVER) != isDiscover
                            ).map(Map.Entry::getKey).toList();

                            return ComputedGroupAclProjection.builder().groupId(entry.getKey())
                                    .isDiscover(isDiscover).specifiedUsers(specifiedUsers)
                                    .build();
                        }).toList() : null;

        // 资源级指定 DISCOVER 权限
        this.specifiedDiscoverUsers = entity.getSpecifiedUsersGrantedActionsMask() != null ?
                entity.getSpecifiedUsersGrantedActionsMask().entrySet().stream().filter(
                        userMask -> ResourceAction.hasAction(userMask.getValue(), ResourceAction.DISCOVER)
                ).map(Map.Entry::getKey).toList() : null;

        Map<String, Integer> specified = entity.getSpecifiedUsersGrantedActionsMask();
        this.courseSpecifiedUsers = specified == null ? List.of() : List.copyOf(specified.keySet());
        this.courseDiscoverUsers = usersWithAction(specified, ResourceAction.DISCOVER, false);
        this.courseViewUsers = usersWithAction(specified, ResourceAction.VIEW, false);
        Map<String, ComputedGroupAcl> groupAcls = entity.getComputedGroupAcls();
        Map<String, Integer> overrides = entity.getOverrideGrantedActionsMask();
        this.courseGroupBinds = entity.getGroupBinds() == null ? List.of() : entity.getGroupBinds().stream()
                .filter(ESIndexEntity::isGroupSearchPlacement)
                .map(bind -> {
                    String groupId = bind.getGroupId();
                    Integer override = overrides == null ? null : overrides.get(groupId);
                    ComputedGroupAcl acl = groupAcls == null ? null : groupAcls.get(groupId);
                    int mask = override != null ? override : acl == null ? 0 : acl.getBaseMask();
                    Map<String, Integer> users = override == null && acl != null ? acl.getUserMasks() : null;
                    boolean discover = ResourceAction.hasAction(mask, ResourceAction.DISCOVER);
                    boolean view = ResourceAction.hasAction(mask, ResourceAction.VIEW);
                    return new CourseGroupBindProjection(groupId, bind.getTagIds(),
                            discover, usersWithAction(users, ResourceAction.DISCOVER, discover),
                            view, usersWithAction(users, ResourceAction.VIEW, view));
                }).toList();
        this.courseProjectionVersion = entity.getUpdateTime() == null ? 0L :
                entity.getUpdateTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
    }

    public static boolean isGroupSearchPlacement(GroupTagBind bind) {
        return bind.getMarketSaleInfo() == null && bind.getGroupId() != null
                && !bind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)
                && bind.getTagIds() != null && !bind.getTagIds().isEmpty();
    }

    private static List<String> usersWithAction(Map<String, Integer> users, ResourceAction action, boolean base) {
        if (users == null) return List.of();
        return users.entrySet().stream()
                .filter(entry -> ResourceAction.hasAction(entry.getValue(), action) != base)
                .map(Map.Entry::getKey).toList();
    }
}
