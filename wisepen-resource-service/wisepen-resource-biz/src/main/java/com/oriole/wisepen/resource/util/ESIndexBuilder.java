package com.oriole.wisepen.resource.util;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.resource.domain.ComputedGroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity.GroupAcl;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 把资源域实体翻译成 ES 索引实体的构建器。
 * <p>
 * 这里集中处理 ACL 编码逻辑，让消费者只负责"接消息 + 调写入"，避免每个消费者重复实现一遍
 * DISCOVER 位过滤。直读 {@link ResourceItemRepository} 而不走 {@code getSystemRawResourceInfo}，
 * 是因为后者的 {@code ResourceItemResponse} 没有 {@code computedGroupAcls}，无法承载情况 D 所需的
 * baseMask / userMasks 原貌。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ESIndexBuilder {

    private final ResourceItemRepository resourceItemRepository;
    private final TagRepository tagRepository;

    /**
     * 根据资源 ID 构建完整索引实体；资源已不存在时返回 empty，调用方按需跳过 Upsert。
     *
     * @param resourceId 资源业务 ID
     * @param rawContent 正文文本（来自上游 MQ，可能为 null 表示本次只写元数据 + ACL）
     */
    public Optional<ESIndexEntity> build(String resourceId, String rawContent) {
        if (StrUtil.isBlank(resourceId)) {
            return Optional.empty();
        }
        return resourceItemRepository.findById(resourceId)
                .map(entity -> toIndexEntity(entity, rawContent));
    }

    private ESIndexEntity toIndexEntity(ResourceItemEntity entity, String rawContent) {
        String typeExt = entity.getResourceType() != null
                ? entity.getResourceType().getExtension()
                : ResourceType.UNKNOWN.getExtension();

        AclProjection acl = projectAcl(entity);
        List<String> tags = collectTagNames(entity);

        // 防 XSS：高亮渲染走 v-html / dangerouslySetInnerHTML，写入前对名称/正文做 HTML 转义
        String safeName = HtmlUtils.htmlEscape(StrUtil.nullToEmpty(entity.getResourceName()));
        String safeContent = rawContent == null ? null : HtmlUtils.htmlEscape(rawContent);

        return ESIndexEntity.builder()
                .id(ESIndexEntity.generateEsId(typeExt, entity.getResourceId()))
                .resourceId(entity.getResourceId())
                .resourceType(typeExt)
                .resourceName(safeName)
                .content(safeContent)
                .allowedUsers(acl.allowedUsers)
                .groupAcls(acl.groupAcls)
                .tags(tags)
                .build();
    }

    /**
     * 把 {@link ResourceItemEntity} 的位掩码 ACL 翻译成 ES 平铺/嵌套字段：
     * <ul>
     *   <li>{@code allowedUsers}：owner + specifiedUsersGrantedActionsMask 中 DISCOVER 位为 1 的用户
     *       + 各 group 的 userMasks 中 DISCOVER 位为 1 的用户</li>
     *   <li>{@code groupAcls}：每个非个人组一条记录，承载 baseDiscover 与 BLACKLIST 否决名单</li>
     * </ul>
     */
    private AclProjection projectAcl(ResourceItemEntity entity) {
        Set<String> allowedUsers = new HashSet<>();
        if (StrUtil.isNotBlank(entity.getOwnerId())) {
            allowedUsers.add(entity.getOwnerId());
        }
        // 情况 B：资源级定向特权
        Map<String, Integer> specifiedMasks = entity.getSpecifiedUsersGrantedActionsMask();
        if (specifiedMasks != null) {
            specifiedMasks.forEach((uid, mask) -> {
                if (mask != null && ResourceAction.hasAction(  mask, ResourceAction.DISCOVER)) {
                    allowedUsers.add(uid);
                }
            });
        }

        List<GroupAcl> groupAcls = new ArrayList<>();
        Map<String, ComputedGroupAcl> computedGroupAcls = entity.getComputedGroupAcls();
        if (computedGroupAcls != null) {
            computedGroupAcls.forEach((groupId, acl) -> {
                int baseMask = acl.getBaseMask() == null ? 0 : acl.getBaseMask();
                boolean baseDiscover = ResourceAction.hasAction(baseMask, ResourceAction.DISCOVER);

                List<String> deniedUsers = new ArrayList<>();
                Map<String, Integer> userMasks = acl.getUserMasks();
                if (userMasks != null) {
                    userMasks.forEach((uid, mask) -> {
                        int m = mask == null ? 0 : mask;
                        // 情况 C：userMasks 中 DISCOVER 位为 1 → 全局白名单；
                        // 否则记入该组的否决名单（BLACKLIST 的典型形态）
                        if (ResourceAction.hasAction(m, ResourceAction.DISCOVER)) {
                            allowedUsers.add(uid);
                        } else {
                            deniedUsers.add(uid);
                        }
                    });
                }
                groupAcls.add(GroupAcl.builder()
                        .groupId(groupId)
                        .baseDiscover(baseDiscover)
                        .explicitlyDeniedUsers(deniedUsers)
                        .build());
            });
        }
        return new AclProjection(new ArrayList<>(allowedUsers), groupAcls);
    }

    private List<String> collectTagNames(ResourceItemEntity entity) {
        if (entity.getGroupBinds() == null || entity.getGroupBinds().isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> allTagIds = entity.getGroupBinds().stream()
                .map(GroupTagBind::getTagIds)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        if (allTagIds.isEmpty()) {
            return new ArrayList<>();
        }
        return StreamSupport.stream(tagRepository.findAllById(allTagIds).spliterator(), false)
                .map(TagEntity::getTagName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private record AclProjection(List<String> allowedUsers, List<GroupAcl> groupAcls) {
    }
}
