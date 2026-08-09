package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.ComputedGroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.MarketSaleInfo;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.dto.req.ResourceRenameRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceUpdateActionPermissionRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceBaseInfoResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceUserInteractionRecordResponse;
import com.oriole.wisepen.resource.domain.entity.FavoriteResourceRef;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceUserInteractionRecordEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.*;
import com.oriole.wisepen.resource.event.ResourceGroupDashboardMetricEvent;
import com.oriole.wisepen.resource.event.TagChangedEvent;
import com.oriole.wisepen.resource.event.TagDeletedEvent;
import com.oriole.wisepen.resource.event.TagTrashedEvent;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.*;
import com.oriole.wisepen.resource.mq.IResourceEventPublisher;
import com.oriole.wisepen.resource.service.assembler.ResourceItemResponseAssembler;
import com.oriole.wisepen.resource.service.IGroupResService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import com.oriole.wisepen.resource.service.ITagService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.enums.ResourceGroupDashboardMetricType;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.oriole.wisepen.resource.constant.ResourceConstants.RESOURCE_TRASH_COLLECTION;
import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.resource.enums.ResourceAction.MARKET_BASE_ACTIONS;
import static com.oriole.wisepen.resource.enums.ResourceAction.MARKET_FORBIDDEN_ACTIONS_MASK;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements IResourceService {

    private final TagRepository tagRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final CustomResourceItemRepository customResourceItemRepository;
    private final GroupResConfigRepository groupResConfigRepository;
    private final ResourceUserInteractionRecordRepository resourceUserInteractRecordRepository;
    private final FavoriteResourceRefRepository favoriteResourceRefRepository;
    private final CustomFavoriteCollectionRepository customFavoriteCollectionRepository;
    private final ResourceCommentRepository resourceCommentRepository;

    private final IResourceEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;

    private final IGroupResService groupResService;
    private final ITagService tagService;
    private final ISearchSyncService searchSyncService;

    private final ResourceItemResponseAssembler resourceItemResponseAssembler;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final RemoteUserService remoteUserService;

    @EventListener
    public void handleTagTrashedEvent(TagTrashedEvent event) {
        int tagCount = event.getTrashedTagIds() == null ? 0 : event.getTrashedTagIds().size();
        log.info("tag trashed event received. tagCount={} tagIds={}",
                tagCount, summarizeIds(event.getTrashedTagIds()));
        try {
            this.stripGroupPermission(event.getTrashedTagIds());
        } catch (Exception e) {
            log.error("tag trashed event handling failed. tagCount={} tagIds={}",
                    tagCount, summarizeIds(event.getTrashedTagIds()), e);
        }
    }

    @EventListener
    public void handleTagChangedEvent(TagChangedEvent event) {
        int tagCount = event.getChangedTagIds() == null ? 0 : event.getChangedTagIds().size();
        log.info("tag changed event received. tagCount={} tagIds={} isPersonalTag={}",
                tagCount, summarizeIds(event.getChangedTagIds()), event.getIsPersonalTag());
        try {
            this.afterTagNodeChanged(event.getChangedTagIds(), event.getIsPersonalTag());
        } catch (Exception e) {
            log.error("tag changed event handling failed. tagCount={} tagIds={} isPersonalTag={}",
                    tagCount, summarizeIds(event.getChangedTagIds()), event.getIsPersonalTag(), e);
        }
    }

    @EventListener
    public void handleTagDeletedEvent(TagDeletedEvent event) {
        int tagCount = event.getDeletedTagIds() == null ? 0 : event.getDeletedTagIds().size();
        log.info("tag deleted event received. tagCount={} tagIds={} isPathTag={}",
                tagCount, summarizeIds(event.getDeletedTagIds()), event.getIsPathTag());
        try {
            this.afterTagNodeDeleted(event.getDeletedTagIds(), event.getIsPersonalTag(), event.getIsPathTag());
        } catch (Exception e) {
            log.error("tag deleted event handling failed. tagCount={} tagIds={} isPathTag={}",
                    tagCount, summarizeIds(event.getDeletedTagIds()), event.getIsPathTag(), e);
        }
    }

    @Override
    public void assertResourceOwner(String resourceId, String userId) {
        ResourceItemEntity entity = getResourceEntity(resourceId);
        if (!userId.equals(entity.getOwnerId())) {
            log.warn("resource permission denied. resourceId={} userId={} ownerId={}",
                    resourceId, userId, entity.getOwnerId());
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
    }

    public ResourceItemEntity getResourceEntity(String resourceId) {
        ResourceItemEntity resource = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.RESOURCE_NOT_FOUND));
        if (resource.getDeletedAt() != null) {
            throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        }
        return resource;
    }

    @Override
    public List<ResourceItemInfoResDTO> listResourceBaseInfo(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) return Collections.emptyList();
        return resourceItemRepository.findAllById(resourceIds).stream()
                .filter(entity -> entity.getDeletedAt() == null)
                .map(resourceItemEntity -> BeanUtil.copyProperties(resourceItemEntity, ResourceItemInfoResDTO.class))
                .toList();
    }

    @Override
    public List<Long> listResourceCountableGroupIds(List<GroupTagBind> groupTagBinds, Map<Long, GroupRoleType> operatorGroupRoles) {
        if (groupTagBinds == null || operatorGroupRoles == null || operatorGroupRoles.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> groupIds = new LinkedHashSet<>();
        for (GroupTagBind groupBind : groupTagBinds) {
            String groupId = groupBind.getGroupId();
            if (!StringUtils.hasText(groupId) || groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
                continue;
            }
            groupId = groupId.startsWith(ResourceConstants.MARKET_GROUP_PREFIX)
                    ? groupId.substring(ResourceConstants.MARKET_GROUP_PREFIX.length()) : groupId;
            try {
                if (operatorGroupRoles.containsKey(Long.valueOf(groupId))) groupIds.add(Long.valueOf(groupId));
            } catch (NumberFormatException ignored) {
            }
        }
        return new ArrayList<>(groupIds);
    }

    @Override
    public void renameResource(ResourceRenameRequest req) {
        ResourceItemEntity entity = getResourceEntity(req.getResourceId());

        String oldName = entity.getResourceName();
        entity.setResourceName(req.getNewName());
        resourceItemRepository.save(entity);
        searchSyncService.syncResourceMetadata(entity, EnumSet.of(UpsertField.RESOURCE_NAME));

        log.info("resource renamed. resourceId={} oldName={} newName={}",
                entity.getResourceId(), oldName, req.getNewName());
    }

    public List<GroupTagBind> updateResourceGroupBinds(List<GroupTagBind> groupBinds, String groupId, List<String> tagIds) {
        if (groupBinds == null) {
            groupBinds = new ArrayList<>();
        }
        if (tagIds == null || tagIds.isEmpty()) {
            // 本次操作清空了该组所有标签，从列表中移除该组
            groupBinds.removeIf(bind -> bind.getGroupId().equals(groupId));
        } else {
            // 寻找该实体中是否已经存在当前 groupId 的绑定记录
            boolean groupFound = false;
            for (GroupTagBind groupBind : groupBinds) {
                if (groupBind.getGroupId().equals(groupId)) {
                    // 找到对应的组，直接覆盖该组的 tagIds
                    groupBind.setTagIds(tagIds);
                    groupFound = true;
                    break;
                }
            }
            // 如果之前这个资源没有在这个组下绑过标签，则新增一个组绑定对象
            if (!groupFound) {
                groupBinds.add(GroupTagBind.builder().groupId(groupId).tagIds(tagIds).build());
            }
        }
        return groupBinds;
    }

    @Override
    public void updatePersonalResourceTags (String resourceId, String groupId, List<String> tagIds) {
        ResourceItemEntity entity = getResourceEntity(resourceId);

        boolean isTrashed = false;

        if (tagIds == null || tagIds.isEmpty()) {
            // 个人空间的资源不允许被清空标签
            throw new ServiceException(ResourceError.CANNOT_BIND_RESOURCE_TO_MULTIPLE_PATH_NODES);
        }

        // 查找并检查Tag
        List<TagEntity> validTags = findAndValidateTags(groupId, tagIds);
        List<TagEntity> pathTags =  validTags.stream().filter(tag -> Boolean.TRUE.equals(tag.getIsPath())).toList();
        // 最多只能有一个 isPath 节点
        if (pathTags.size() != 1) throw new ServiceException(ResourceError.CANNOT_BIND_RESOURCE_TO_MULTIPLE_PATH_NODES);
        // 首位 (Index 0) 的节点必须是这个唯一的 isPath 节点
        if (!tagIds.getFirst().equals(pathTags.getFirst().getTagId())) throw new ServiceException(ResourceError.CANNOT_PLACE_RESOURCE_PATH_TAG_AFTER_TAGS);

        // 检查目标路径是否属于回收站
        if (tagService.isNodeInTrash(groupId, pathTags.getFirst().getTagId()) != ITagService.TagType.NOT_IN_TRASH) {
            isTrashed = true;
            // 移入回收站会卸载除了个人组的所有节点，如果此前有发布到市场，则还需移除市场索引
            entity.getGroupBinds().stream()
                    .filter(bind -> bind.getMarketSaleInfo() != null)
                    .forEach(bind -> searchSyncService.deleteMarketResourceIndexesByResourceIdAndMarketGroupId(
                            entity.getResourceId(), bind.getGroupId()));
            entity.getGroupBinds().removeIf(bind -> !bind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX));
            entity.setOverrideGrantedActionsMask(null);
            entity.setSpecifiedUsersGrantedActionsMask(null);
            entity.setComputedGroupAcls(null);
        }

        entity.setGroupBinds(updateResourceGroupBinds(entity.getGroupBinds(), groupId, tagIds));
        resourceItemRepository.save(entity);
        log.info("resource tags changed. resourceId={} groupId={} tagCount={}",
                entity.getResourceId(), groupId, tagIds.size());
        if (isTrashed) {
            eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "STRIP_GROUP_PERMISSION");
        }
    }

    @Override
    public void updateGroupResourceTags(String resourceId, String groupId, String userId, GroupRoleType groupRole, List<String> tagIds) {
        ResourceItemEntity entity = getResourceEntity(resourceId);
        updateGroupResourceTags(entity, groupId, userId, groupRole, tagIds);
    }

    public void updateGroupResourceTags(ResourceItemEntity entity, String groupId, String userId, GroupRoleType groupRole, List<String> tagIds) {
        if (tagIds != null && !tagIds.isEmpty()) {
            // 查找并检查Tag
            // MARKET 组的 Tag 无法通过这种方法找到（在 MARKET_GROUP_PREFIX 前缀的 groupId 下）因此无法通过该方法绑定
            List<TagEntity> validTags = findAndValidateTags(groupId, tagIds);

            // 小组 FOLDER 模式：同一小组内每个资源至多挂载一个标签
            FileOrganizationLogic logic = groupResService.getFileOrgLogic(groupId);
            if (FileOrganizationLogic.FOLDER == logic && tagIds.size() > 1)
                throw new ServiceException(ResourceError.CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE);

            // 检查是否有权限挂载
            if (groupRole == null || groupRole == GroupRoleType.NOT_MEMBER) {
                throw new ServiceException(ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED);
            }
            if (groupRole != GroupRoleType.ADMIN && groupRole != GroupRoleType.OWNER) {
                checkGroupMemberTagMountPermission(userId, validTags);
            }
        }

        entity.setGroupBinds(updateResourceGroupBinds(entity.getGroupBinds(), groupId, tagIds));
        resourceItemRepository.save(entity);
        log.info("resource tags changed. resourceId={} groupId={} tagCount={}",
                entity.getResourceId(), groupId, tagIds == null ? 0 : tagIds.size());
        eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "RESOURCE_TAGS_CHANGED");
    }

    public List<TagEntity> findAndValidateTags(String groupId, List<String> tagIds) {
        List<TagEntity> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
        }
        boolean allBelongToGroup = tags.stream().allMatch(tag -> groupId.equals(tag.getGroupId()));
        if (!allBelongToGroup) {
            throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
        }
        return tags;
    }

    public void checkGroupMemberTagMountPermission(String userId, List<TagEntity> tags) {
        for (TagEntity tag : tags) {
            ResolvedTagPermission resolved = resolveTagMountConfig(tag);
            boolean canMount = (resolved.tagMountPermissionScope == AccessControlScope.ALL ||
                    (resolved.tagMountPermissionScope == AccessControlScope.WHITELIST && resolved.tagMountSpecifiedUsers.contains(userId)) ||
                    (resolved.tagMountPermissionScope == AccessControlScope.BLACKLIST && !resolved.tagMountSpecifiedUsers.contains(userId)));
            if (!canMount) {
                throw new ServiceException(ResourceError.BIND_RESOURCE_TO_TAG_NODE_DENIED);
            }
        }
    }

    @Override
    public void updateResourceActionPermission(ResourceUpdateActionPermissionRequest req){
        ResourceItemEntity entity = getResourceEntity(req.getResourceId());

        // 设置小组权限覆盖
        if (req.getOverrideGrantedActions() != null) {
            Map<String, Integer> overrideMaskMap = entity.getOverrideGrantedActionsMask() != null ? entity.getOverrideGrantedActionsMask() : new HashMap<>() ;
            req.getOverrideGrantedActions().forEach((groupId, actions) -> {
                // 要覆盖的小组必须是已经绑定了的
                entity.getGroupBinds().stream().filter(groupTagBind -> groupTagBind.getGroupId().equals(groupId)).findFirst().ifPresent(groupTagBind ->{
                    if (groupTagBind.getMarketSaleInfo() != null){ // Market 组 override 只能由上架/审核流程维护
                        return;
                    }
                    // 传 null 代表清空该组的覆盖规则，走默认群组标签规则 (下同)
                    if (actions != null) {
                        overrideMaskMap.put(groupId, ResourceAction.actionsToPermissionCode(actions));
                    } else {
                        overrideMaskMap.remove(groupId);
                    }
                });
            });
            entity.setOverrideGrantedActionsMask(overrideMaskMap);
        }

        if (req.getSpecifiedUsersGrantedActions() != null) {
            Map<String, Integer> specifiedMaskMap = new HashMap<>();
            req.getSpecifiedUsersGrantedActions().forEach((uid, actionsList) ->
                    specifiedMaskMap.put(uid, ResourceAction.actionsToPermissionCode(actionsList)));
            entity.setSpecifiedUsersGrantedActionsMask(specifiedMaskMap);
        } else {
            entity.setSpecifiedUsersGrantedActionsMask(null);
        }

        resourceItemRepository.save(entity);
        log.info("resource action permission changed. resourceId={} hasOverride={} specifiedUserCount={}",
                entity.getResourceId(),
                entity.getOverrideGrantedActionsMask() != null,
                entity.getSpecifiedUsersGrantedActionsMask() == null ? 0 : entity.getSpecifiedUsersGrantedActionsMask().size());

        // 保存资源级权限覆盖后，触发重算
        eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "RESOURCE_ACTION_PERMISSION_CHANGED");
    }

    @Override
    public ResourceBaseInfoResponse getResourceBaseInfo(String resourceId, String currentUserId, Map<Long, GroupRoleType> groupRoles){
        ResourceItemEntity entity = getResourceEntity(resourceId);

        // Market 组的权限在此不起作用
        ResourceItemResponseAssembler.ResolvedResourceAccess resourceAccess =
                resourceItemResponseAssembler.resolveAccess(entity, currentUserId, groupRoles, null, true);
        List<ResourceAction> resourceActions = ResourceAction.permissionCodeToActions(resourceAccess.getActionsMask());

        if (!resourceActions.contains(ResourceAction.DISCOVER)) {
            log.warn("resource base info permission denied. resourceId={} userId={}", entity.getResourceId(), currentUserId);
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }

        ResourceBaseInfoResponse response = BeanUtil.copyProperties(entity, ResourceBaseInfoResponse.class);
        if (!resourceActions.contains(ResourceAction.VIEW)) {
            // 预览仅对有 VIEW 权限的用户生效
            response.setPreview(null);
        }

        Long owner = Long.valueOf(entity.getOwnerId());
        Map<Long, UserDisplayBase> ownerInfoMap = remoteUserService.getUserDisplayInfo(List.of(owner)).getData();
        UserDisplayBase ownerInfo = ownerInfoMap.get(owner);
        response.setOwnerInfo(ownerInfo == null ? new UserDisplayBase("UNKNOWN") : ownerInfo);

        return response;
    }

    @Override
    public ResourceItemResponse getResourceInfo(ResourceInfoGetReqDTO dto) {
        ResourceItemEntity entity = getResourceEntity(dto.getResourceId());

        // 组装 ResourceItemResponse（过滤无 ResourceAction.VIEW 权限的）
        ResourceItemResponse resourceItemResponse = resourceItemResponseAssembler.assembleOne(entity, dto.getUserId().toString(), dto.getGroupRoles(), List.of(ResourceAction.VIEW), dto.getTargetVersion(), true);
        if (resourceItemResponse == null) {
            log.warn("resource permission denied. resourceId={} userId={}", entity.getResourceId(), dto.getUserId());
            throw new ServiceException(ResourceError.RESOURCE_PERMISSION_DENIED);
        }
        return resourceItemResponse;
    }

    @Override
    public PageR<ResourceItemResponse> listResources(String currentUserId,
                                                     String groupId, GroupRoleType userGroupRole,
                                                     Map<Long, GroupRoleType> groupRoles,
                                                     List<String> tagIds, QueryLogicEnum tagQueryLogicMode,
                                                     Boolean includeMyInteraction,
                                                     String resourceType, int page, int size,
                                                     ResourceSortBy sortBy, SortDirectionEnum sortDir) {

        List<String> excludeTrashIds = null;
        // 如果是个人空间，且没有明确指定要查回收站，必须把回收站设为黑名单
        if (groupId != null && groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
            TagEntity trashNode = tagRepository.findByGroupIdAndParentIdAndTagName(
                    groupId, "0", ResourceConstants.TRASH_TAG_NAME).orElse(null);

            if (trashNode != null) {
                // 只有在“全局查询/全部文件”（前端未传特定 tagId）时，才需要拉黑回收站
                // 如果前端传了 tagIds，说明用户是在明确浏览某个特定文件夹（哪怕它已经在回收站里），此时不应拦截。
                if (tagIds == null || tagIds.isEmpty()) {
                    // 查出回收站体系下的所有子孙文件夹
                    List<TagEntity> descendants = tagRepository.findByGroupIdAndAncestorsContaining(groupId, trashNode.getTagId());
                    excludeTrashIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
                    excludeTrashIds.add(trashNode.getTagId());
                }
            }
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(sortDir.toSpringDirection(), sortBy.getDbField()));

        Page<ResourceItemEntity> entityPage = customResourceItemRepository.findAccessibleResources(
                currentUserId, groupId, userGroupRole, tagIds, excludeTrashIds, tagQueryLogicMode, resourceType, pageable);

        // 批量组装 ResourceItemResponse（不需要再权限过滤）
        // 不检查 Market 版本
        List<ResourceItemResponse> responses = resourceItemResponseAssembler.assembleMany(entityPage.getContent(), currentUserId, groupRoles, List.of(), null, false);

        if (groupId != null) {
            // 过滤非检索groupId的标签
            responses.forEach(response -> {
                response.setTagBinds(response.getTagBinds().stream()
                        .filter(tagBind -> Objects.equals(tagBind.getGroupId(), groupId) || Objects.equals(tagBind.getGroupId(), ResourceConstants.MARKET_GROUP_PREFIX + groupId))
                        .toList());
            });
        }

        List<String> resourceIds = responses.stream().map(ResourceItemResponse::getResourceId).toList();
        if (Boolean.TRUE.equals(includeMyInteraction) && !resourceIds.isEmpty()) {
            Map<String, ResourceUserInteractionRecordEntity> interactionMap = resourceUserInteractRecordRepository
                    .findByUserIdAndResourceIdIn(currentUserId, resourceIds).stream()
                    .collect(Collectors.toMap(
                            ResourceUserInteractionRecordEntity::getResourceId,
                            interactionRecord -> interactionRecord,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new));
            responses.forEach(response -> {
                ResourceUserInteractionRecordEntity interactionRecord = interactionMap.getOrDefault(
                        response.getResourceId(), new ResourceUserInteractionRecordEntity(response.getResourceId(), currentUserId));
                response.setMyInteractionRecord(BeanUtil.copyProperties(interactionRecord, ResourceUserInteractionRecordResponse.class));
            });
        }

        PageR<ResourceItemResponse> pageR = new PageR<>(entityPage.getTotalElements(), page, size);
        pageR.addAll(responses);
        return pageR;
    }

    @Override
    public String createResourceItem(ResourceCreateReqDTO dto) {
        ResourceItemEntity entity = new ResourceItemEntity();
        BeanUtil.copyProperties(dto, entity);
        resourceItemRepository.save(entity);

        String personalGroupId = ResourceConstants.PERSONAL_GROUP_PREFIX + dto.getOwnerId();

        String mountTargetTagID = dto.getMountTargetTagId();
        // 获取 Tag 信息
        TagEntity mountTargetTag = StringUtils.hasText(mountTargetTagID) ?
                tagRepository.findById(mountTargetTagID).orElse(null) : null;
        // mountTargetTag 为空时，获取个人的根目录
        if (mountTargetTag == null) {
            mountTargetTagID = tagRepository.findByGroupIdAndParentIdAndTagName(
                    personalGroupId, "0", ResourceConstants.ROOT_TAG_NAME
            ).orElseThrow(() -> new ServiceException(ResourceError.TAG_NODE_NOT_FOUND)).getTagId();
        }

        try {
            if (mountTargetTag != null && !mountTargetTag.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
                // 小组 Tag 需要先挂 个人.Shared 目录，再挂目标小组标签
                String sharedTagId = tagRepository.findByGroupIdAndParentIdAndTagName(
                        personalGroupId, "0", ResourceConstants.SHARED_TAG_NAME).orElseThrow(
                        () -> new ServiceException(ResourceError.TAG_NODE_NOT_FOUND)
                ).getTagId();
                this.updatePersonalResourceTags(entity.getResourceId(), personalGroupId, List.of(sharedTagId));

                try {
                    // 确定用户有权限挂载到对应位置
                    GroupRoleType groupRole = dto.getOwnerGroupRoles().get(Long.valueOf(mountTargetTag.getGroupId()));
                    // 挂载标签
                    updateGroupResourceTags(entity, mountTargetTag.getGroupId(), dto.getOwnerId(), groupRole, List.of(mountTargetTagID));
                } catch (Exception ignored) {
                    // 如果没有权限或出现其他错误，静默失败
                    // TODO: 给用户发送站内信
                }
            } else {
                // 个人 Tag 直接更新
                this.updatePersonalResourceTags(entity.getResourceId(), personalGroupId, List.of(mountTargetTagID));
            }
        } catch (Exception e) {
            // 创建资源失败，回滚
            resourceItemRepository.deleteById(entity.getResourceId());
            log.warn("resource item compensated. resourceId={}", entity.getResourceId(), e);
            throw e;
        }
        // resourceInteractionInfo 已内嵌在 ResourceItemEntity 中，无需单独初始化
        Long actorUserId = Long.valueOf(dto.getOwnerId());
        listResourceCountableGroupIds(entity.getGroupBinds(), dto.getOwnerGroupRoles()).forEach(groupId -> applicationEventPublisher.publishEvent(new ResourceGroupDashboardMetricEvent(groupId, entity.getResourceId(), actorUserId, ResourceGroupDashboardMetricType.RESOURCE_ADDED, 1)));
        // 同步初始化资源搜索记录
        searchSyncService.syncResourceMetadata(entity, EnumSet.of(UpsertField.RESOURCE_TYPE, UpsertField.RESOURCE_NAME, UpsertField.ACL));

        log.info("resource created. resourceId={} ownerId={} resourceType={} mountTargetTagId={}",
                entity.getResourceId(), dto.getOwnerId(), dto.getResourceType(), dto.getMountTargetTagId());
        return entity.getResourceId();
    }

    @Override
    public void softRemoveResources(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        List<ResourceItemEntity> entities = resourceItemRepository.findAllById(resourceIds);
        if (entities.isEmpty()) {
            return;
        }
        for (ResourceItemEntity entity : entities) {
            entity.setDeletedAt(LocalDateTime.now());
            mongoTemplate.save(entity, RESOURCE_TRASH_COLLECTION); // 插入到回收集合（用于审计）中
            // 软删除时删除搜索索引，以避免可以被搜索到
            searchSyncService.deleteResourceIndex(entity.getResourceId());
            searchSyncService.deleteMarketResourceIndexesByResourceId(entity.getResourceId());

        }
        resourceItemRepository.deleteAllById(resourceIds);// 从业务表中物理擦除
        log.info("resources deleted. mode=soft count={} resourceIds={}",
                entities.size(), summarizeIds(resourceIds));
    }

    @Override
    public void hardRemoveResources(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        // 仅从审计集合中物理擦除
        Query query = Query.query(Criteria.where("_id").in(resourceIds));

        List<ResourceItemEntity> expiredResources = mongoTemplate.find(
                query,
                ResourceItemEntity.class,
                ResourceConstants.RESOURCE_TRASH_COLLECTION
        );
        if (expiredResources.isEmpty()) return;

        long deletedCount = mongoTemplate.remove(query, RESOURCE_TRASH_COLLECTION).getDeletedCount();

        if (deletedCount > 0) {
            List<String> deletedResourceIds = expiredResources.stream()
                .map(ResourceItemEntity::getResourceId)
                .collect(Collectors.toList());

            // 清理评论集合中的孤立数据
            resourceCommentRepository.deleteAllByResourceIdIn(deletedResourceIds);

            // 清理资源对应的用户行为记录表
            resourceUserInteractRecordRepository.deleteAllByResourceIdIn(deletedResourceIds);

            // 清理收藏集合中的孤立引用
            List<FavoriteResourceRef> deletedFavoriteRefs = favoriteResourceRefRepository.findByResourceIdIn(deletedResourceIds);
            // 扣减各个集合的收藏计数
            Map<String, Integer> collectionCountDeltas = new HashMap<>();
            for (FavoriteResourceRef ref : deletedFavoriteRefs) {
                if (ref.getCollectionIds() == null) continue;
                ref.getCollectionIds().stream()
                        .filter(StringUtils::hasText)
                        .forEach(collectionId -> collectionCountDeltas.merge(collectionId, -1, Integer::sum));
            }
            for (Map.Entry<String, Integer> entry: collectionCountDeltas.entrySet()) {
                customFavoriteCollectionRepository.updateItemCount(Set.of(entry.getKey()), entry.getValue());
            }
            // 清理资源对应的收藏行为记录表
            favoriteResourceRefRepository.deleteByResourceIdIn(deletedResourceIds);

            log.info("resources deleted. mode=hard count={} resourceIds={}",
                    deletedCount, summarizeIds(resourceIds));

            // 发送 Kafka 广播，通知文件存储等下游微服务抹除物理文件
            eventPublisher.publishResDeletedEvent(expiredResources);
        }
    }

    @Override
    public void updateResourceAttributes(ResourceUpdateReqDTO dto) {
        resourceItemRepository.findById(dto.getResourceId()).ifPresentOrElse(entity -> {
            BeanUtil.copyProperties(dto, entity, CopyOptions.create().ignoreNullValue());
            resourceItemRepository.save(entity);
            log.info("resource attributes updated. resourceId={}", entity.getResourceId());
        }, () -> log.warn("resource attributes update skipped. resourceId={}", dto.getResourceId()));
    }

    @Override
    public void afterTagNodeChanged(List<String> changedTagIds, Boolean isPersonalTag) {
        if (isPersonalTag) {
            return; // 个人Tag变更不需要重新计算Acl
        }
        if (changedTagIds == null || changedTagIds.isEmpty())
            return;
        // 查询所有涉及的资源绑定记录
        List<ResourceItemEntity> affectedBinds = resourceItemRepository.findByTagIdsIn(changedTagIds);
        List<String> affectedResourceIds = affectedBinds.stream()
                .map(ResourceItemEntity::getResourceId).collect(Collectors.toList());
        // 循环触发权限预计算
        for (ResourceItemEntity bind : affectedBinds) {
            eventPublisher.publishAclRecalculateEvent(bind.getResourceId(), "TAG_CHANGED");
        }
        log.info("acl recalculation dispatched. mode=batch tagCount={} tagIds={} affectedResources={} affectedResourceIds={}",
                changedTagIds.size(), summarizeIds(changedTagIds),
                affectedBinds.size(), summarizeIds(affectedResourceIds));
    }

    @Override
    public void afterTagNodeDeleted(List<String> deletedTagIds, Boolean isPersonalTag, Boolean isPathTag) {
        if (deletedTagIds == null || deletedTagIds.isEmpty()) return;

        // 查询所有涉及的资源绑定记录
        List<ResourceItemEntity> affectedBinds = resourceItemRepository.findByTagIdsIn(deletedTagIds);
        if (affectedBinds.isEmpty()) {
            return;
        }

        // 如果是路径(FOLDER Tag)被彻底销毁，触发资源的软删除
        if (Boolean.TRUE.equals(isPathTag)) {
            for (ResourceItemEntity entity : affectedBinds) {
                // 插入到回收集合（用于审计）中
                entity.setDeletedAt(LocalDateTime.now());
                mongoTemplate.save(entity, RESOURCE_TRASH_COLLECTION);
                // 软删除时删除搜索索引，以避免可以被搜索到
                searchSyncService.deleteResourceIndex(entity.getResourceId());
                searchSyncService.deleteMarketResourceIndexesByResourceId(entity.getResourceId());
            }
            // 从业务表中物理擦除
            resourceItemRepository.deleteAll(affectedBinds);
            List<String> trashedResourceIds = affectedBinds.stream()
                    .map(ResourceItemEntity::getResourceId).collect(Collectors.toList());
            log.info("resources deleted. mode=soft count={} resourceIds={}",
                    affectedBinds.size(), summarizeIds(trashedResourceIds));
            // 资源已经彻底从业务流中消失，直接返回，无需重算 ACL
        } else {
            List<String> recalcResourceIds = new ArrayList<>();
            for (ResourceItemEntity entity : affectedBinds) {
                if (entity.getGroupBinds() != null) {
                    Iterator<GroupTagBind> iterator = entity.getGroupBinds().iterator();
                    while (iterator.hasNext()) {
                        GroupTagBind groupBind = iterator.next();

                        // 移除掉已经被删除的 Tag ID
                        if (groupBind.getTagIds() != null) {
                            groupBind.getTagIds().removeAll(deletedTagIds);
                        }

                        // 如果移除后该组下没有任何 Tag
                        if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) {
                            // 普通组清理空组
                            if (groupBind.getMarketSaleInfo() == null) {
                                iterator.remove();
                            } else { // 集市组保留绑定，但走下架流程
                                entity.offShelfMarketSaleInfo(groupBind.getGroupId());
                                // 同步集市组搜索
                                searchSyncService.syncMarketResourceIndex(entity, groupBind.getGroupId());
                            }
                        }
                    }
                }
                resourceItemRepository.save(entity);
                if (isPersonalTag) {
                    continue; // 个人Tag变更不需要重新计算Acl
                }
                eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "TAG_DELETED");
                recalcResourceIds.add(entity.getResourceId());
            }
            log.info("acl recalculation dispatched. mode=batch tagCount={} tagIds={} affectedResources={} affectedResourceIds={}",
                    deletedTagIds.size(), summarizeIds(deletedTagIds),
                    recalcResourceIds.size(), summarizeIds(recalcResourceIds));
        }
    }

    public void stripGroupPermission(List<String> trashedTagIds){
        if (trashedTagIds == null || trashedTagIds.isEmpty()) return;

        Query query = new Query(Criteria.where("groupBinds.tagIds").in(trashedTagIds));
        List<ResourceItemEntity> affectedResources = mongoTemplate.find(query, ResourceItemEntity.class);

        if (!affectedResources.isEmpty()) {
            for (ResourceItemEntity entity : affectedResources) {
                // 移入回收站会卸载除了个人组的所有节点，如果此前有发布到市场，则还需移除市场索引
                entity.getGroupBinds().stream()
                        .filter(bind -> bind.getMarketSaleInfo() != null)
                        .forEach(bind -> searchSyncService.deleteMarketResourceIndexesByResourceIdAndMarketGroupId(
                                entity.getResourceId(), bind.getGroupId()));
                entity.getGroupBinds().removeIf(bind -> !bind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX));
                entity.setOverrideGrantedActionsMask(null);
                entity.setSpecifiedUsersGrantedActionsMask(null);
                entity.setComputedGroupAcls(null);
            }
            resourceItemRepository.saveAll(affectedResources);
            List<String> affectedResourceIds = affectedResources.stream()
                    .map(ResourceItemEntity::getResourceId).collect(Collectors.toList());
            log.info("group permission stripped. affectedResources={} affectedResourceIds={}",
                    affectedResources.size(), summarizeIds(affectedResourceIds));
            for (ResourceItemEntity entity : affectedResources) {
                eventPublisher.publishAclRecalculateEvent(entity.getResourceId(), "STRIP_GROUP_PERMISSION");
            }
        }
    }

    @Override
    public Optional<ResourceItemEntity> calculateResourceGroupAcl(String resourceId) {
        long start = System.currentTimeMillis();
        log.debug("acl recalculation started. resourceId={}", resourceId);
        // 获取资源绑定记录
        ResourceItemEntity bindEntity = resourceItemRepository.findByResourceId(resourceId)
                .orElse(null);

        if (bindEntity == null) {
            log.warn("acl recalculation skipped. resourceId={}", resourceId);
            return Optional.empty();
        }

        Map<String, ComputedGroupAcl> computedGroupAcls = new HashMap<>();

        if (bindEntity.getGroupBinds() != null && !bindEntity.getGroupBinds().isEmpty()) {
            for (GroupTagBind groupBind : bindEntity.getGroupBinds()) {
                if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) continue;
                if (groupBind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) continue; // 个人Tag不参与计算Acl

                String groupId = groupBind.getGroupId();
                // 获取资源级组权限覆盖
                Integer overrideMask = bindEntity.getOverrideGrantedActionsMask() == null ? null : bindEntity.getOverrideGrantedActionsMask().get(groupId);

                if (groupBind.getMarketSaleInfo() != null) { // 当前组是 MARKET 组
                    ComputedGroupAcl computed = new ComputedGroupAcl();
                    MarketSaleInfo marketSaleInfo = groupBind.getMarketSaleInfo(); // 售卖配置

                    int baseMask = 0;
                    if (overrideMask != null) { // 优先资源级组权限覆盖
                        // 不能存在 MARKET_FORBIDDEN_ACTIONS_MASK 中的权限
                        baseMask = overrideMask & ~MARKET_FORBIDDEN_ACTIONS_MASK;
                    } else if (marketSaleInfo != null && marketSaleInfo.getStatus() == MarketSaleStatus.PUBLISHED) {
                        // 如果不存在 ReviewActionsMask，以 MARKET_BASE_ACTIONS 为准
                        // 不能存在 MARKET_FORBIDDEN_ACTIONS_MASK 中的权限
                        baseMask = (marketSaleInfo.getReviewActionsMask() != null ? marketSaleInfo.getReviewActionsMask() : MARKET_BASE_ACTIONS) & ~MARKET_FORBIDDEN_ACTIONS_MASK;
                    }
                    computed.setBaseMask(baseMask);

                    // marketSaleInfo 存在且状态为 PUBLISHED
                    if (marketSaleInfo != null && marketSaleInfo.getStatus() == MarketSaleStatus.PUBLISHED && marketSaleInfo.getMarketSpecifiedUsersGrantedActionsMask() != null) {
                        // 遍历 MarketSpecifiedUsersGrantedActionsMask，将这些用户和对应权限添加到 UserMasks 列表中
                        int userBaseMask = baseMask;
                        marketSaleInfo.getMarketSpecifiedUsersGrantedActionsMask().forEach((uid, mask) ->
                                // 不能存在 MARKET_FORBIDDEN_ACTIONS_MASK 中的权限
                                computed.getUserMasks().put(uid, (userBaseMask | mask) & ~MARKET_FORBIDDEN_ACTIONS_MASK));
                    }
                    computedGroupAcls.put(groupId, computed);
                    continue;
                }

                // 查询所有 Tag 详情
                List<TagEntity> tags = tagRepository.findAllById(groupBind.getTagIds());

                // Tag 详情传递至 groupBind，供后续使用
                // TODO: groupBind.tags 暂无后续使用
                groupBind.setTags(tags);

                // 提取首标
                TagEntity primaryTag = tags.stream().filter(
                        tagEntity -> tagEntity.getTagId().equals(groupBind.getTagIds().getFirst())
                ).findFirst().orElse(null);

                if (primaryTag == null) continue;

                Integer defaultActions = groupResConfigRepository.findByGroupId(groupId)
                        .map(GroupResConfigEntity::getDefaultMemberActionsMask)
                        .orElse(ResourceAction.DEFAULT_MEMBER_ACTIONS);
                ResolvedTagPermission resolved = resolveTagAclGrantConfig(primaryTag, defaultActions);

                // 如果资源自身有覆盖小组权限，则优先使用覆盖权限作为基础分发掩码
                Integer effectiveMask = overrideMask != null ? overrideMask : resolved.taggedResourceGrantedActionsMask;

                // 将 TaggedResourceAclGrantScope 编译为 BaseMask 和 UserMasks
                ComputedGroupAcl computed = new ComputedGroupAcl();
                switch (resolved.taggedResourceAclGrantScope) {
                    case ALL:
                        computed.setBaseMask(effectiveMask);
                        break;
                    case ONLY_ADMIN:
                        computed.setBaseMask(0);
                        break;
                    case WHITELIST:
                        computed.setBaseMask(0);
                        resolved.taggedResourceAclGrantSpecifiedUsers.forEach(uid -> computed.getUserMasks().put(uid, effectiveMask));
                        break;
                    case BLACKLIST:
                        computed.setBaseMask(effectiveMask);
                        resolved.taggedResourceAclGrantSpecifiedUsers.forEach(uid -> computed.getUserMasks().put(uid, 0));
                        break;
                }
                computedGroupAcls.put(groupId, computed);
            }
        }

        Query query = new Query(Criteria.where("_id").is(resourceId));
        Update update = new Update()
                .set("computedGroupAcls", computedGroupAcls)
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceItemEntity.class);
        log.debug("acl recalculation finished. resourceId={} groupCount={} costMs={}",
                resourceId, computedGroupAcls.size(), System.currentTimeMillis() - start);

        bindEntity.setComputedGroupAcls(computedGroupAcls);
        bindEntity.setUpdateTime(LocalDateTime.now());
        return Optional.of(bindEntity);
    }

    @Override
    // checkPermission 不能依赖预计算ACL，必须重新计算
    public ResourceCheckPermissionResDTO checkPermission(ResourceCheckPermissionReqDTO dto) {
        // 如果资源不存在（或已进入回收站），直接拒绝
        ResourceItemEntity entity = resourceItemRepository.findById(dto.getResourceId()).orElse(null);
        if (entity == null) {
            return logResolved(dto, new ResourceCheckPermissionResDTO(ResourceAccessRole.NONE));
        }

        // 资源所有者有全部权限
        if (dto.getUserId().toString().equals(entity.getOwnerId())) {
            return logResolved(dto, new ResourceCheckPermissionResDTO(ResourceAccessRole.OWNER, null,
                    ResourceAction.permissionCodeToActions(ResourceAction.ALL_ACTIONS)));
        }

        // 检查资源级的“指定用户特权”
        // 优先级：定向用户特权 (userMask) > 群组策略覆盖 (Override) > 群组策略 (actionsMask)
        Integer specifiedMask = entity.getSpecifiedUsersGrantedActionsMask() == null ? null :
                entity.getSpecifiedUsersGrantedActionsMask().get(dto.getUserId().toString());
        if (specifiedMask != null) {
            return logResolved(dto, new ResourceCheckPermissionResDTO(ResourceAccessRole.OWNER_SPECIFIED, new HashSet<>(), ResourceAction.permissionCodeToActions(specifiedMask)));
        }

        // 判断是否缺乏群组上下文（用户不在任何组 或 资源不在任何组）
        boolean noGroupContext = (dto.getGroupRoles() == null || dto.getGroupRoles().isEmpty()) ||
                (entity.getGroupBinds() == null || entity.getGroupBinds().isEmpty());

        // 如果没有群组上下文，也没有被单独赋予“指定用户特权”，直接拒绝
        if (noGroupContext) {
            return logResolved(dto, new ResourceCheckPermissionResDTO(ResourceAccessRole.NONE));
        }

        ResourceAccessRole resourceAccessRole = ResourceAccessRole.NONE;
        int actionsMask = 0;
        int marketActionsMask = 0;
        Set<String> permissionSources = new HashSet<>();
        // 计算群组权限
        for (GroupTagBind groupBind : entity.getGroupBinds()) {         // 遍历资源绑定的所有组
            if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) continue;
            if (groupBind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) continue; // 个人Tag不参与计算
            String groupId = groupBind.getGroupId();

            if (!dto.getGroupRoles().containsKey(Long.valueOf(groupId))) { // 用户不在该组，跳过
                continue;
            }
            GroupRoleType userRoleInThisGroup = dto.getGroupRoles().get(Long.valueOf(groupId));

            // 用户是组管理员/拥有者，有全部权限
            if (userRoleInThisGroup == GroupRoleType.ADMIN || userRoleInThisGroup == GroupRoleType.OWNER) {
                if (groupBind.getMarketSaleInfo() != null) {
                    // 不能存在 MARKET_FORBIDDEN_ACTIONS_MASK 中的权限
                    return logResolved(dto, new ResourceCheckPermissionResDTO(ResourceAccessRole.GROUP_ADMIN,
                            new HashSet<>(Collections.singleton(groupBind.getGroupId())),
                            ResourceAction.permissionCodeToActions(ResourceAction.ALL_ACTIONS & ~MARKET_FORBIDDEN_ACTIONS_MASK)));
                } else {
                    return logResolved(dto, new ResourceCheckPermissionResDTO(ResourceAccessRole.GROUP_ADMIN,
                            new HashSet<>(Collections.singleton(groupBind.getGroupId())),
                            ResourceAction.permissionCodeToActions(ResourceAction.ALL_ACTIONS)));
                }
            }

            Integer overrideMask = entity.getOverrideGrantedActionsMask() == null ? null : entity.getOverrideGrantedActionsMask().get(groupId);

            if (groupBind.getMarketSaleInfo() != null) { // 当前组是 MARKET 组
                MarketSaleInfo marketSaleInfo = groupBind.getMarketSaleInfo();

                // 未携带资源 targetVersion 时不能使用 MARKET 组鉴权，跳过
                if (dto.getTargetVersion() == null || !dto.getTargetVersion().equals(marketSaleInfo.getOfferVersion())){
                    continue;
                }

                // MARKET 组资源 marketSaleInfo 状态不是 PUBLISHED，跳过
                if (marketSaleInfo.getStatus() != MarketSaleStatus.PUBLISHED) {
                    continue;
                }

                int resolvedMarketMask;
                if (overrideMask != null) { // 优先资源级组权限覆盖
                    resolvedMarketMask = overrideMask;
                } else {
                    // 如果不存在 ReviewActionsMask，以 MARKET_BASE_ACTIONS 为准
                    resolvedMarketMask = marketSaleInfo.getReviewActionsMask() != null ? marketSaleInfo.getReviewActionsMask() : MARKET_BASE_ACTIONS;
                    if (marketSaleInfo.getMarketSpecifiedUsersGrantedActionsMask() != null) {
                        resolvedMarketMask |= marketSaleInfo.getMarketSpecifiedUsersGrantedActionsMask().getOrDefault(dto.getUserId().toString(), 0);
                    }
                }
                // 不能存在 MARKET_FORBIDDEN_ACTIONS_MASK 中的权限
                resolvedMarketMask = resolvedMarketMask & ~MARKET_FORBIDDEN_ACTIONS_MASK;

                if (resolvedMarketMask != 0) {
                    if (resourceAccessRole == ResourceAccessRole.NONE) {
                        resourceAccessRole = ResourceAccessRole.GROUP_MEMBER;
                    }
                    permissionSources.add(groupBind.getGroupId());
                    marketActionsMask |= resolvedMarketMask;
                }
                continue;
            }

            // 应用资源群组策略覆盖
            // 优先级：群组策略覆盖 (Override) > 群组策略 (actionsMask)
            if (overrideMask != null) {
                if (overrideMask != 0) {
                    if (resourceAccessRole == ResourceAccessRole.NONE) resourceAccessRole = ResourceAccessRole.GROUP_MEMBER;
                    permissionSources.add(groupBind.getGroupId());
                    actionsMask |= overrideMask;
                }
                continue;
            }

            // 提取首标并查询
            String primaryTagId = groupBind.getTagIds().getFirst();
            // 查询首标
            TagEntity primaryTag = tagRepository.findById(primaryTagId).orElse(null);
            if (primaryTag == null) continue;

            Integer defaultActions = groupResConfigRepository.findByGroupId(groupBind.getGroupId())
                    .map(GroupResConfigEntity::getDefaultMemberActionsMask)
                    .orElse(ResourceAction.DEFAULT_MEMBER_ACTIONS);

            ResolvedTagPermission resolved = resolveTagAclGrantConfig(primaryTag, defaultActions);

            // 使用 TaggedResourceAclGrantScope 判断用户是否能获取当前组的权限掩码
            boolean isEligibleForMask = (resolved.taggedResourceAclGrantScope == AccessControlScope.ALL ||
                    (resolved.taggedResourceAclGrantScope == AccessControlScope.WHITELIST && resolved.taggedResourceAclGrantSpecifiedUsers.contains(dto.getUserId().toString())) ||
                    (resolved.taggedResourceAclGrantScope == AccessControlScope.BLACKLIST && !resolved.taggedResourceAclGrantSpecifiedUsers.contains(dto.getUserId().toString())));

            if (isEligibleForMask && resolved.taggedResourceGrantedActionsMask != 0 ) {
                // 只要有一个组能下发权限（无权限(0)不在此列），基础身份就是 GROUP_MEMBER
                if (resourceAccessRole == ResourceAccessRole.NONE) {
                    resourceAccessRole = ResourceAccessRole.GROUP_MEMBER;
                }
                permissionSources.add(groupBind.getGroupId());
                // 累加普通成员在不同小组下获得的权限 (按位或)
                actionsMask |= resolved.taggedResourceGrantedActionsMask;
            }
        }

        // 合并 actionsMask 和 marketActionsMask
        return logResolved(dto, new ResourceCheckPermissionResDTO(resourceAccessRole, permissionSources,
                ResourceAction.permissionCodeToActions(actionsMask | marketActionsMask)));
    }

    /**
     * 鉴权出口统一打 DEBUG，便于排查权限问题
     */
    private ResourceCheckPermissionResDTO logResolved(ResourceCheckPermissionReqDTO dto, ResourceCheckPermissionResDTO result) {
        log.debug("permission resolved. resourceId={} userId={} role={} actions={}",
                dto.getResourceId(), dto.getUserId(), result.getResourceAccessRole(), result.getAllowedActions());
        return result;
    }

    /**
     * 标签权限溯源的聚合结果
     */
    @Data
    private static class ResolvedTagPermission {
        AccessControlScope taggedResourceAclGrantScope;
        List<String> taggedResourceAclGrantSpecifiedUsers = Collections.emptyList();
        Integer taggedResourceGrantedActionsMask;
        AccessControlScope tagMountPermissionScope;
        List<String> tagMountSpecifiedUsers = Collections.emptyList();

        boolean isTaggedResourceAclGrantScope() { return taggedResourceAclGrantScope != null; }
        boolean isTaggedResourceGrantedActionsMaskResolved() { return taggedResourceGrantedActionsMask != null; }
        boolean isTagMountPermissionScopeResolved() { return tagMountPermissionScope != null; }
        boolean isFullyResolved() {
            return isTaggedResourceAclGrantScope() && isTaggedResourceGrantedActionsMaskResolved() && isTagMountPermissionScopeResolved();
        }
    }

    private ResolvedTagPermission resolveTagAclGrantConfig(TagEntity node, Integer defaultActions) {
        ResolvedTagPermission result = resolveTagPermission(node);
        // 无论是根节点本身没配权限，还是遍历完所有祖先都没找到配置，都会在这里被安全拦截
        // 如果未解析到动作权限，则使用默认动作权限
        if (!result.isTaggedResourceGrantedActionsMaskResolved()) {
            result.taggedResourceGrantedActionsMask = defaultActions;
        }
        // 如果未解析到 ACL授予模式，则使用默认ACL授予模式（ALL）
        if (!result.isTaggedResourceAclGrantScope()) {
            result.taggedResourceAclGrantScope = AccessControlScope.ALL;
        }
        return result;
    }

    private ResolvedTagPermission resolveTagMountConfig(TagEntity node) {
        ResolvedTagPermission result = resolveTagPermission(node);
        // 如果未解析到资源挂载模式，则使用默认挂载模式（ALL）
        if (!result.isTagMountPermissionScopeResolved()) {
            result.tagMountPermissionScope = AccessControlScope.ALL;
        }
        return result;
    }

    /**
     * 单次向上遍历标签树，分别捕获 ACL授予模式 和 授予动作掩码 最近的非空配置
     */
    private ResolvedTagPermission resolveTagPermission(TagEntity node) {
        ResolvedTagPermission result = new ResolvedTagPermission();

        // 先尝试从当前节点捕获
        capturePermission(result, node);

        // 如果当前节点未能完全解析，且存在祖先节点，则向上溯源
        if (!result.isFullyResolved()) {

            List<String> ancestors = node.getAncestors();
            if (ancestors != null && !ancestors.isEmpty()) {

                // 批量查询祖先节点
                Iterable<TagEntity> ancestorEntities = tagRepository.findAllById(ancestors);

                Map<String, TagEntity> ancestorMap = new HashMap<>();
                for (TagEntity entity : ancestorEntities) {
                    ancestorMap.put(entity.getTagId(), entity); // 建立TagID与实体的映射
                }

                for (int i = ancestors.size() - 1; i >= 0; i--) { // 从 ancestors 列表的最后一个元素开始往回遍历到第一个元素
                    TagEntity ancestorNode = ancestorMap.get(ancestors.get(i));
                    if (ancestorNode != null) {
                        capturePermission(result, ancestorNode);
                        if (result.isFullyResolved())
                            break; // 如果均捕获到，直接返回
                    }
                }
            }
        }
        return result;
    }

    /**
     * 将节点的权限配置按维度填入聚合结果，仅填充尚未解析的维度
     */
    private void capturePermission(ResolvedTagPermission result, TagEntity node) {
        if (!result.isTaggedResourceAclGrantScope() && node.getTaggedResourceAclGrantScope() != null) {
            result.taggedResourceAclGrantScope = node.getTaggedResourceAclGrantScope();
            result.taggedResourceAclGrantSpecifiedUsers = node.getTaggedResourceAclGrantSpecifiedUsers() != null ?
                    node.getTaggedResourceAclGrantSpecifiedUsers() : Collections.emptyList();
        }
        if (!result.isTaggedResourceGrantedActionsMaskResolved() && (node.getTaggedResourceGrantedActionsMask() != null)) {
            result.taggedResourceGrantedActionsMask = node.getTaggedResourceGrantedActionsMask();
        }
        if (!result.isTagMountPermissionScopeResolved() && node.getTagMountPermissionScope() != null) {
            result.tagMountPermissionScope = node.getTagMountPermissionScope();
                result.tagMountSpecifiedUsers = node.getTagMountSpecifiedUsers() != null ?
                        node.getTagMountSpecifiedUsers() : Collections.emptyList();
        }
    }
}
