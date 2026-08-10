package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.mq.IResourceEventPublisher;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.resource.service.IGroupResService;
import com.oriole.wisepen.resource.service.IResourcePlacementService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import com.oriole.wisepen.resource.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePlacementServiceImpl implements IResourcePlacementService {

    private final ResourceItemRepository resourceItemRepository;
    private final TagRepository tagRepository;
    private final IGroupResService groupResService;
    private final ITagService tagService;
    private final ISearchSyncService searchSyncService;
    private final IResourceEventPublisher eventPublisher;
    private final IResourceService resourceService;

    @Override
    public void setPersonalResourcesPathTag(List<String> resourceIds, String userId, String newPathTagId) {
        String groupId = ResourceConstants.PERSONAL_GROUP_PREFIX + userId;
        // 查找并检查Tag
        TagEntity targetTag = resourceService.findAndValidateTags(groupId, List.of(newPathTagId)).getFirst();
        // 目标节点必须是一个路径节点
        if (!Boolean.TRUE.equals(targetTag.getIsPath())) {
            throw new ServiceException(ResourceError.RESOURCE_TAG_TYPE_INVALID);
        }

        List<ResourceItemEntity> entities = findValidResourceEntities(resourceIds);
        List<ResourceItemEntity> trashedEntities = new ArrayList<>();
        // 检查目标路径是否属于回收站
        boolean isTrashed = tagService.isNodeInTrash(groupId, newPathTagId) != ITagService.TagType.NOT_IN_TRASH;

        for (ResourceItemEntity entity : entities) {
            List<String> currentTagIds = entity.getGroupBinds().stream()
                    .filter(bind -> groupId.equals(bind.getGroupId())).findFirst().map(GroupTagBind::getTagIds)
                    .orElseGet(ArrayList::new);

            currentTagIds.set(0, newPathTagId); // 修改首节点

            if (isTrashed) {
                // 移入回收站会卸载除了个人组的所有节点，如果此前有发布到市场，则还需移除市场索引
                entity.getGroupBinds().stream()
                        .filter(bind -> bind.getMarketSaleInfo() != null)
                        .forEach(bind -> searchSyncService.deleteMarketResourceIndexesByResourceIdAndMarketGroupId(
                                entity.getResourceId(), bind.getGroupId()));
                entity.getGroupBinds().removeIf(bind -> !bind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX));

                entity.setOverrideGrantedActionsMask(null);
                entity.setSpecifiedUsersGrantedActionsMask(null);
                entity.setComputedGroupAcls(null);
                trashedEntities.add(entity);
            }
            entity.setGroupBinds(resourceService.updateResourceGroupBinds(entity.getGroupBinds(), groupId, currentTagIds));
        }

        resourceItemRepository.saveAll(entities);
        log.info("personal resource path moved. affectedResources={} affectedResourceIds={} groupId={} newPathTagId={} trashed={}",
                entities.size(), summarizeIds(entities.stream().map(ResourceItemEntity::getResourceId).toList()),
                groupId, newPathTagId, isTrashed);
        for (ResourceItemEntity entity : trashedEntities) {
            eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "STRIP_GROUP_PERMISSION");
        }
    }

    @Override
    public void movePersonalResourcesToTrash(List<String> resourceIds, String userId) {
        String trashTagId = tagRepository.findByGroupIdAndParentIdAndTagName(
                        ResourceConstants.PERSONAL_GROUP_PREFIX + userId, "0", ResourceConstants.TRASH_TAG_NAME)
                .map(TagEntity::getTagId)
                .orElseThrow(() -> new ServiceException(ResourceError.TAG_NODE_NOT_FOUND));
        setPersonalResourcesPathTag(resourceIds, userId, trashTagId);
    }

    @Override
    public void replacePersonalNormalTags(List<String> resourceIds, String userId, List<String> normalTagIds) {
        String groupId = ResourceConstants.PERSONAL_GROUP_PREFIX + userId;
        List<ResourceItemEntity> entities = findValidResourceEntities(resourceIds);

        // 查找并检查Tag
        List<TagEntity> normalTags = resourceService.findAndValidateTags(groupId, normalTagIds);

        // 普通 Tag 不能是 isPath 节点
        if (normalTags.stream().anyMatch(tag -> Boolean.TRUE.equals(tag.getIsPath()))) {
            throw new ServiceException(ResourceError.RESOURCE_TAG_TYPE_INVALID);
        }

        for (ResourceItemEntity entity : entities) {
            List<String> currentTagIds = entity.getGroupBinds().stream()
                    .filter(bind -> groupId.equals(bind.getGroupId())).findFirst().map(GroupTagBind::getTagIds)
                    .orElseGet(ArrayList::new);

            List<String> targetTagIds = new ArrayList<>();
            targetTagIds.add(currentTagIds.getFirst()); // 保留首 isPath 节点
            targetTagIds.addAll(normalTagIds);

            entity.setGroupBinds(resourceService.updateResourceGroupBinds(entity.getGroupBinds(), groupId, targetTagIds));
        }

        resourceItemRepository.saveAll(entities);
        log.info("personal resource normal tags replaced. affectedResources={} affectedResourceIds={} groupId={} normalTagCount={}",
                entities.size(), summarizeIds(entities.stream().map(ResourceItemEntity::getResourceId).toList()),
                groupId, normalTagIds == null ? 0 : normalTagIds.size());
    }

    @Override
    public void mountResourcesToGroup(List<String> resourceIds, String groupId, String userId, GroupRoleType groupRole, String targetTagId) {
        // 查找并检查Tag
        List<TagEntity> targetTags = resourceService.findAndValidateTags(groupId, List.of(targetTagId));
        List<ResourceItemEntity> entities = findValidResourceEntities(resourceIds);

        // 检查是否有权限挂载
        if (groupRole == null || groupRole == GroupRoleType.NOT_MEMBER) {
            throw new ServiceException(ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED);
        }
        if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
            resourceService.checkGroupMemberTagMountPermission(userId, targetTags);
        }

        FileOrganizationLogic logic = groupResService.getFileOrgLogic(groupId);

        for (ResourceItemEntity entity : entities) {
            List<String> currentTagIds = entity.getGroupBinds().stream()
                    .filter(bind -> groupId.equals(bind.getGroupId())).findFirst().map(GroupTagBind::getTagIds)
                    .orElseGet(ArrayList::new);

            if (!currentTagIds.contains(targetTagId)) currentTagIds.add(targetTagId);

            // 小组 FOLDER 模式：同一小组内每个资源至多挂载一个标签
            if (FileOrganizationLogic.FOLDER == logic && currentTagIds.size() > 1) {
                throw new ServiceException(ResourceError.CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE);
            }

            entity.setGroupBinds(resourceService.updateResourceGroupBinds(entity.getGroupBinds(), groupId, currentTagIds));
        }
        resourceItemRepository.saveAll(entities);
        log.info("group resources mounted. affectedResources={} affectedResourceIds={} groupId={} tagCount={}",
                entities.size(),
                summarizeIds(entities.stream().map(ResourceItemEntity::getResourceId).toList()),
                groupId,
                targetTags.size());
        for (ResourceItemEntity entity : entities) {
            eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "RESOURCE_TAGS_CHANGED");
        }
    }

    @Override
    public void unmountResourcesToGroup(Map<String, String> resourceSourceTagMap, String groupId, String userId, GroupRoleType groupRole) {
        Map<String, String> sourceMap = cleanMap(resourceSourceTagMap);

        // 查找并检查Tag
        resourceService.findAndValidateTags(groupId, new ArrayList<>(new LinkedHashSet<>(sourceMap.values())));
        List<ResourceItemEntity> entities = findValidResourceEntities(new ArrayList<>(sourceMap.keySet()));

        for (ResourceItemEntity entity : entities) {
            String sourceTagId = sourceMap.get(entity.getResourceId());
            List<String> currentTagIds = entity.getGroupBinds().stream()
                    .filter(bind -> groupId.equals(bind.getGroupId())).findFirst().map(GroupTagBind::getTagIds)
                    .orElseGet(ArrayList::new);

            if (currentTagIds.getFirst().equals(sourceTagId)){
                // 移除了首标签，权限可能发生变动，解除全部挂载
                currentTagIds = null; // 设为 null 即可解除全部挂载
            } else {
                currentTagIds.remove(sourceTagId); // 解除挂载
            }

            entity.setGroupBinds(resourceService.updateResourceGroupBinds(entity.getGroupBinds(), groupId, currentTagIds));
        }
        resourceItemRepository.saveAll(entities);
        log.info("group resources unmounted. affectedResources={} affectedResourceIds={} groupId={} tagCount={}",
                entities.size(),
                summarizeIds(entities.stream().map(ResourceItemEntity::getResourceId).toList()),
                groupId,
                sourceMap.size());
        for (ResourceItemEntity entity : entities) {
            eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "RESOURCE_TAGS_CHANGED");
        }
    }

    @Override
    public void moveResourcesInGroup(Map<String, String> resourceSourceTagMap, String groupId, String userId, GroupRoleType groupRole, String targetTagId) {
        Map<String, String> sourceMap = cleanMap(resourceSourceTagMap);
        List<String> validateTagIds = new ArrayList<>(new LinkedHashSet<>(sourceMap.values()));
        validateTagIds.add(targetTagId);
        // 查找并检查Tag
        List<TagEntity> validTags = resourceService.findAndValidateTags(groupId, cleanIds(validateTagIds));
        Optional<TagEntity> targetTag = validTags.stream().filter(tag -> targetTagId.equals(tag.getTagId())).findFirst();
        assert targetTag.isPresent();

        List<ResourceItemEntity> entities = findValidResourceEntities(new ArrayList<>(sourceMap.keySet()));

        // 不是删除时需要检查挂载权限
        // 检查是否有权限挂载
        if (groupRole == null || groupRole == GroupRoleType.NOT_MEMBER) {
            throw new ServiceException(ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED);
        }
        if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
            resourceService.checkGroupMemberTagMountPermission(userId, List.of(targetTag.get()));
        }

        FileOrganizationLogic logic = groupResService.getFileOrgLogic(groupId);
        for (ResourceItemEntity entity : entities) {
            String sourceTagId = sourceMap.get(entity.getResourceId());

            List<String> currentTagIds = entity.getGroupBinds().stream()
                    .filter(bind -> groupId.equals(bind.getGroupId())).findFirst().map(GroupTagBind::getTagIds)
                    .orElseGet(ArrayList::new);

            int sourceIndex = currentTagIds.indexOf(sourceTagId);

            if (sourceIndex < 0) {
                throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
            }
            if (!sourceTagId.equals(targetTagId)) {
                // 如果当前 Tag 中有目标 Tag，就只移除源 Tag
                if (currentTagIds.contains(targetTagId)) {
                    currentTagIds.remove(sourceIndex);
                } else { // 否则替换源 Tag
                    currentTagIds.set(sourceIndex, targetTagId);
                }
            }
            currentTagIds = new ArrayList<>(new LinkedHashSet<>(currentTagIds));

            // 小组 FOLDER 模式：同一小组内每个资源至多挂载一个标签
            if (FileOrganizationLogic.FOLDER == logic && currentTagIds.size() > 1) {
                throw new ServiceException(ResourceError.CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE);
            }

            entity.setGroupBinds(resourceService.updateResourceGroupBinds(entity.getGroupBinds(), groupId, currentTagIds));
        }

        resourceItemRepository.saveAll(entities);
        log.info("group resources moved. affectedResources={} affectedResourceIds={} groupId={} tagCount={}",
                entities.size(),
                summarizeIds(entities.stream().map(ResourceItemEntity::getResourceId).toList()),
                groupId,
                sourceMap.size());
        for (ResourceItemEntity entity : entities) {
            eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "RESOURCE_TAGS_CHANGED");
        }
    }

    private List<ResourceItemEntity> findValidResourceEntities(List<String> resourceIds) {
        resourceIds = cleanIds(resourceIds);
        List<ResourceItemEntity> entities = resourceItemRepository.findAllById(resourceIds);
        Set<String> foundResourceIds = entities.stream()
                .filter(entity -> entity.getDeletedAt() == null)
                .map(ResourceItemEntity::getResourceId)
                .collect(Collectors.toSet());
        if (foundResourceIds.size() != resourceIds.size() || !foundResourceIds.containsAll(resourceIds)) {
            throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        }
        return entities;
    }

    private List<String> cleanIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        return ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private Map<String, String> cleanMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, String> result = source.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue()))
                .collect(Collectors.toMap(
                        entry -> entry.getKey().trim(),
                        entry -> entry.getValue().trim(),
                        (first, second) -> second,
                        LinkedHashMap::new
                ));
        return result;
    }
}
