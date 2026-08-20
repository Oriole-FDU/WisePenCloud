package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.dto.req.TagCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagMoveRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.TagTreeResponse;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.event.TagChangedEvent;
import com.oriole.wisepen.resource.event.TagDeletedEvent;
import com.oriole.wisepen.resource.event.TagTrashedEvent;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.resource.service.ITagService;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.resource.constant.ResourceConstants.TAGS_TRASH_COLLECTION;
import static com.oriole.wisepen.resource.exception.ResourceError.CANNOT_SET_TAG_NODE_VISIBILITY;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements ITagService {

    private final TagRepository tagRepository;
    private final MongoTemplate mongoTemplate;
    private final RemoteUserService remoteUserService;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String createTag(TagCreateRequest tagCreateRequest, String creatorUserId) {
        String groupID = tagCreateRequest.getGroupId();

        // 检查是否是 Market 组
        if (!groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
            String rawGroupId = groupID.startsWith(ResourceConstants.MARKET_GROUP_PREFIX)
                    ? groupID.substring(ResourceConstants.MARKET_GROUP_PREFIX.length())
                    : groupID;

            // 检查 groupID对应的小组是否是 MARKET_GROUP，如果是则添加MARKET_GROUP_PREFIX，否则不添加
            Map<Long, GroupDisplayBase> groupMap = remoteUserService.getGroupDisplayInfo(List.of(Long.valueOf(rawGroupId))).getData();
            GroupDisplayBase groupInfo = groupMap == null ? null : groupMap.get(Long.valueOf(rawGroupId));
            groupID = groupInfo != null && groupInfo.getGroupType() == GroupType.MARKET_GROUP
                    ? ResourceConstants.MARKET_GROUP_PREFIX + rawGroupId : rawGroupId;

            tagCreateRequest.setGroupId(groupID);
        }
        String parentId = tagCreateRequest.getParentId();
        String tagName = tagCreateRequest.getTagName();

        // 禁止在回收站及其子目录下创建任何新节点
        if (isNodeInTrash(groupID, parentId) != TagType.NOT_IN_TRASH) {
            throw new ServiceException(ResourceError.CANNOT_OPERATE_TRASHED_TAG_PATH_NODE);
        }

        // 禁止建立系统级保留节点 Tag
        if (isSystemPathTagName(tagName)) {
            throw new ServiceException(ResourceError.CANNOT_USE_RESERVED_TAG_PATH_NODE_NAME);
        }

        // 校验同级重名
        tagRepository.findByGroupIdAndParentIdAndTagName(groupID, parentId, tagName)
                .ifPresent(t -> { throw new ServiceException(ResourceError.TAG_NODE_NAME_CONFLICT); });

        TagEntity entity = new TagEntity();

        BeanUtil.copyProperties(tagCreateRequest, entity);
        entity.setTagCreator(creatorUserId);
        if (tagCreateRequest.getGrantedActions() != null) {
            entity.setTaggedResourceGrantedActionsMask(ResourceAction.actionsToPermissionCode(tagCreateRequest.getGrantedActions()));
        }

        if (groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)){
            // 个人组标签不能设置标签权限
            entity.setTaggedResourceAclGrantScope(null);
            entity.setTaggedResourceAclGrantSpecifiedUsers(null);
            entity.setTaggedResourceGrantedActionsMask(null);
            entity.setTagMountPermissionScope(null);
            entity.setTagMountSpecifiedUsers(null);
        }

        // 计算祖先数组 (核心逻辑)
        if (parentId != null && !"0".equals(parentId)) {
            TagEntity parent = tagRepository.findByGroupIdAndTagId(groupID, parentId)
                    .orElseThrow(() -> new ServiceException(ResourceError.PARENT_TAG_NODE_NOT_FOUND));

//            // 跨类型校验，FOLDER Tag只能在FOLDER Tag下创建，Normal Tag只能在Normal Tag下创建
//            if (!Objects.equals(parent.getIsPath(), entity.getIsPath())) {
//                throw new ServiceException(ResPermissionErrorCode.CANNOT_MOVE_TAG_NODE_ACROSS_TAG_TYPE);
//            }

            // 设定Tag身份
            // FOLDER Tag只能在FOLDER Tag下创建，Normal Tag只能在Normal Tag下创建
            entity.setIsPath(parent.getIsPath());

            List<String> newAncestors = new ArrayList<>(parent.getAncestors() == null ?
                    Collections.emptyList() : parent.getAncestors());
            newAncestors.add(parent.getTagId());
            entity.setAncestors(newAncestors);
        } else {
            entity.setParentId("0");
            entity.setIsPath(false);
            entity.setAncestors(new ArrayList<>());
        }

        TagEntity saved = tagRepository.save(entity);
        log.info("tag created. groupId={} parentId={} tagId={} isPath={}",
                saved.getGroupId(), saved.getParentId(), saved.getTagId(), saved.getIsPath());
        return saved.getTagId();
    }

    @Override
    public List<TagTreeResponse> getTagTree(String groupId) {
        // 一次性查出该组所有节点，避免 N+1 查询问题
        List<TagEntity> allTags = tagRepository.findByGroupId(groupId);

        ensurePersonalSystemTags(groupId, allTags);

        Set<Long> creatorUserIds = allTags.stream()
                .map(TagEntity::getTagCreator)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, UserDisplayBase> creatorInfoMap = Collections.emptyMap();
        if (!creatorUserIds.isEmpty()) {
            try {
                Map<Long, UserDisplayBase> fetched = remoteUserService.getUserDisplayInfo(new ArrayList<>(creatorUserIds)).getData();
                creatorInfoMap = fetched == null ? Collections.emptyMap() : fetched;
            } catch (Exception e) {
                log.warn("tag creator info batch degraded. groupId={} creatorCount={}",
                        groupId, creatorUserIds.size(), e);
            }
        }

        // 转换为 DTO
        Map<Long, UserDisplayBase> finalCreatorInfoMap = creatorInfoMap;
        List<TagTreeResponse> tagTreeResponseList = allTags.stream().map(entity -> {
            TagTreeResponse tagTreeResponse = new TagTreeResponse();
            BeanUtil.copyProperties(entity, tagTreeResponse);
            if (StringUtils.hasText(entity.getTagCreator())) {
                tagTreeResponse.setCreatorInfo(finalCreatorInfoMap.get(Long.valueOf(entity.getTagCreator())));
            }
            int taggedResourceGrantedActionsMask = entity.getTaggedResourceGrantedActionsMask() == null ? 0 : entity.getTaggedResourceGrantedActionsMask();
            tagTreeResponse.setGrantedActions(ResourceAction.permissionCodeToActions(taggedResourceGrantedActionsMask));
            tagTreeResponse.setChildren(new ArrayList<>());
            return tagTreeResponse;
        }).collect(Collectors.toList());

        // 在内存中组装树状结构 (比在 DB 中递归查快得多)
        return buildTree(tagTreeResponseList, "0");
    }

    @Override
    public void ensurePersonalSystemTags(String groupId) {
        ensurePersonalSystemTags(groupId, tagRepository.findByGroupId(groupId));
    }

    private void ensurePersonalSystemTags(String groupId, List<TagEntity> allTags) {
        if (groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
            boolean hasRoot = false;
            boolean hasTrash = false;
            boolean hasShared = false;

            for (TagEntity tag : allTags) {
                if ("0".equals(tag.getParentId())) {
                    if (ResourceConstants.ROOT_TAG_NAME.equals(tag.getTagName())) hasRoot = true;
                    if (ResourceConstants.TRASH_TAG_NAME.equals(tag.getTagName())) hasTrash = true;
                    if (ResourceConstants.SHARED_TAG_NAME.equals(tag.getTagName())) hasShared = true;
                }
            }

            boolean initialized = false;
            if (!hasRoot) {
                TagEntity entity = TagEntity.builder().groupId(groupId).ancestors(new ArrayList<>())
                        .tagName(ResourceConstants.ROOT_TAG_NAME).parentId("0").isPath(true).build();
                allTags.add(tagRepository.save(entity));
                initialized = true;
            }
            if (!hasTrash) {
                TagEntity entity = TagEntity.builder().groupId(groupId).ancestors(new ArrayList<>())
                        .tagName(ResourceConstants.TRASH_TAG_NAME).parentId("0").isPath(true).build();
                allTags.add(tagRepository.save(entity));
                initialized = true;
            }
            if (!hasShared) {
                TagEntity entity = TagEntity.builder().groupId(groupId).ancestors(new ArrayList<>())
                        .tagName(ResourceConstants.SHARED_TAG_NAME).parentId("0").isPath(true).build();
                allTags.add(tagRepository.save(entity));
                initialized = true;
            }

            if (initialized) {
                log.info("personal space created. groupId={} nodes=root,trash,shared", groupId);
            }
        }
    }

    // --- 更新 Tag (Update) ---
    @Override
    public void updateTag(TagUpdateRequest tagUpdateRequest) {
        String groupID = tagUpdateRequest.getGroupId();
        String targetId = tagUpdateRequest.getTargetTagId();

        // 严禁修改处于回收站中的任何节点属性
        if (isNodeInTrash(groupID, targetId) == TagType.IN_TRASH) {
            throw new ServiceException(ResourceError.CANNOT_OPERATE_TRASHED_TAG_PATH_NODE);
        }

        TagEntity entity = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResourceError.TAG_NODE_NOT_FOUND));

        String newName = tagUpdateRequest.getTagName();

        if (newName != null && !newName.equals(entity.getTagName())) {
            // 名称变动时，校验在当前父节点下是否重名
            tagRepository.findByGroupIdAndParentIdAndTagName(groupID, entity.getParentId(), newName)
                    .ifPresent(t -> { throw new ServiceException(ResourceError.TAG_NODE_NAME_CONFLICT); });
        }

        // 禁止修改系统级保留 Tag
        if (isSystemPathTagName(entity.getTagName())) {
            throw new ServiceException(ResourceError.CANNOT_MODIFY_SYSTEM_TAG_PATH_NODE);
        }

        // 禁止改名为系统级保留节点 Tag
        if (isSystemPathTagName(newName)) {
            throw new ServiceException(ResourceError.CANNOT_USE_RESERVED_TAG_PATH_NODE_NAME);
        }

        // 是否有权限变更
        boolean isPermissionChanged = (tagUpdateRequest.getTaggedResourceAclGrantScope() != null && !tagUpdateRequest.getTaggedResourceAclGrantScope().equals(entity.getTaggedResourceAclGrantScope()))
                || (tagUpdateRequest.getTagMountPermissionScope() != null && !tagUpdateRequest.getTagMountPermissionScope().equals(entity.getTagMountPermissionScope())
                || (tagUpdateRequest.getTaggedResourceAclGrantSpecifiedUsers() != null && !tagUpdateRequest.getTaggedResourceAclGrantSpecifiedUsers().equals(entity.getTaggedResourceAclGrantSpecifiedUsers()))
                || (tagUpdateRequest.getTagMountSpecifiedUsers() != null && !tagUpdateRequest.getTagMountSpecifiedUsers().equals(entity.getTagMountSpecifiedUsers())
                || (tagUpdateRequest.getGrantedActions() != null && ResourceAction.actionsToPermissionCode(tagUpdateRequest.getGrantedActions()) != entity.getTaggedResourceGrantedActionsMask())));

        if (groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX) && isPermissionChanged){
            throw new ServiceException(CANNOT_SET_TAG_NODE_VISIBILITY); // 个人组标签不能设置标签权限
        }

        boolean nameChanged = newName != null && !newName.equals(entity.getTagName());

        // 更新基本信息和权限策略
        tagUpdateRequest.setIsPath(null); // IsPath始终不允许修改
        BeanUtil.copyProperties(tagUpdateRequest, entity, CopyOptions.create().ignoreNullValue()); // 非空时更新
        // 额外处理GrantedActions的Mask转换
        if (tagUpdateRequest.getGrantedActions() != null) { // 非空时更新
            entity.setTaggedResourceGrantedActionsMask(ResourceAction.actionsToPermissionCode(tagUpdateRequest.getGrantedActions()));
        }

        tagRepository.save(entity);

        log.info("tag updated. groupId={} tagId={} nameChanged={} permissionChanged={}",
                groupID, targetId, nameChanged, isPermissionChanged);

        if (isPermissionChanged) {
            log.info("tag permission changed. groupId={} tagId={} taggedResourceAclGrantScope={} tagMountPermissionScope={}",
                    groupID, targetId, entity.getTaggedResourceAclGrantScope(), entity.getTagMountPermissionScope());
            // 通知所有挂在它以及它子孙节点上的资源重新计算权限
            // 个人组标签不能设置标签权限，已经提前抛出错误
            afterTagNodeChanged(groupID, targetId, false);
        }
    }

    @Override
    public void moveTags(TagMoveRequest tagMoveRequest) {
        String groupID = tagMoveRequest.getGroupId();
        List<String> targetIds = tagMoveRequest.getTargetTagIds().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.toList());
        String newParentId = tagMoveRequest.getNewParentId() == null ? "0" : tagMoveRequest.getNewParentId();

        // 严禁向回收站内部节点移动Tag
        // 用户可把外面的节点移入回收站
        if (isNodeInTrash(groupID, newParentId) == TagType.IN_TRASH) {
            throw new ServiceException(ResourceError.CANNOT_OPERATE_TRASHED_TAG_PATH_NODE);
        }

        // 批量目标存在性校验
        List<TagEntity> targetNodes = tagRepository.findByGroupIdAndTagIdIn(groupID, targetIds);
        if (targetNodes.size() != targetIds.size()) {
            throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
        }

        // 如果父节点都没变直接返回
        if (targetNodes.stream().allMatch(targetNode -> newParentId.equals(targetNode.getParentId()))) {
            return;
        }

        Set<String> targetIdSet = new LinkedHashSet<>(targetIds);
        for (TagEntity targetNode : targetNodes) {
            // 同一批次不允许同时出现祖先和子孙节点
            if (targetNode.getAncestors() != null && targetNode.getAncestors().stream().anyMatch(targetIdSet::contains)) {
                throw new ServiceException(ResourceError.CANNOT_OPERATE_ANCESTOR_DESCENDANT_TAG_NODES);
            }

            // 目标父节点不能是本批次任一移动节点
            if (newParentId.equals(targetNode.getTagId())) {
                throw new ServiceException(ResourceError.CANNOT_MOVE_TAG_NODE_TO_SELF);
            }

            // 系统级保留节点禁止移动位置
            if (isSystemPathTagName(targetNode.getTagName())) {
                throw new ServiceException(ResourceError.CANNOT_MOVE_SYSTEM_TAG_PATH_NODE);
            }
        }

        // 移动到新位置前，校验目标目录下是否有同名节点
        // 回收站例外，允许有同名节点
        if (isNodeInTrash(groupID, newParentId) != TagType.TRASH) {
            List<String> movingNames = targetNodes.stream().map(TagEntity::getTagName).toList();
            if (movingNames.size() != new LinkedHashSet<>(movingNames).size()) {
                throw new ServiceException(ResourceError.TAG_NODE_NAME_CONFLICT); // 待移动节点本身重名
            }
            Query nameConflictQuery = Query.query(Criteria.where("groupId").is(groupID)
                    .and("parentId").is(newParentId)
                    .and("tagName").in(movingNames));
            List<TagEntity> conflictingNodes = mongoTemplate.find(nameConflictQuery, TagEntity.class);
            boolean hasExternalConflict = conflictingNodes.stream()
                    .anyMatch(conflictingNode -> !targetIdSet.contains(conflictingNode.getTagId())); // 目标节点下有节点与待移动节点重名
            if (hasExternalConflict) {
                throw new ServiceException(ResourceError.TAG_NODE_NAME_CONFLICT);
            }
        }

        // 获取目标父节点 & 防环形依赖校验
        // 绝对不能把一个节点拖拽到它自己的子孙节点下面，否则会形成死循环树
        List<String> newParentAncestors = new ArrayList<>();
        if (!"0".equals(newParentId)) {
            TagEntity newParentNode = tagRepository.findByGroupIdAndTagId(groupID, newParentId)
                    .orElseThrow(() -> new ServiceException(ResourceError.PARENT_TAG_NODE_NOT_FOUND));

            for (TagEntity targetNode : targetNodes) { // 批量检查
                // 跨类型移动校验，防止将 Normal Tag 拖入 FOLDER Tag 或将 FOLDER Tag 拖入 Normal Tag
                if (!Objects.equals(targetNode.getIsPath(), newParentNode.getIsPath())) {
                    throw new ServiceException(ResourceError.CANNOT_MOVE_TAG_NODE_ACROSS_TAG_TYPE);
                }

                // 目标父节点不能是自己的子孙节点
                if (newParentNode.getAncestors() != null && newParentNode.getAncestors().contains(targetNode.getTagId())) {
                    throw new ServiceException(ResourceError.CANNOT_MOVE_TAG_NODE_TO_DESCENDANT);
                }
            }

            if (newParentNode.getAncestors() != null) {
                newParentAncestors.addAll(newParentNode.getAncestors());
            }
            newParentAncestors.add(newParentId); // 新父节点的 ancestors + 新父节点自身
        }

        // 在批量移动标签时找出所有被移动节点的子孙节点，准备一批要保存的实体
        Query descendantsQuery = Query.query(Criteria.where("groupId").is(groupID)
                .and("ancestors").in(targetIds));
        List<TagEntity> descendants = mongoTemplate.find(descendantsQuery, TagEntity.class);
        List<TagEntity> entitiesToUpdate = new ArrayList<>();

        for (TagEntity targetNode : targetNodes) {
            targetNode.setParentId(newParentId);
            targetNode.setAncestors(new ArrayList<>(newParentAncestors));
            entitiesToUpdate.add(targetNode);
        }

        // 遍历并重算所有子孙节点的 ancestors
        for (TagEntity descendant : descendants) {
            List<String> oldDescendantAncestors = descendant.getAncestors();

            String matchedRootId = oldDescendantAncestors.stream().filter(targetIdSet::contains).findFirst().orElseThrow();

            // 新的祖先路径 = 移动根节点的新祖先 + 移动根节点本身 + 原子树内部路径
            List<String> newDescendantAncestors = new ArrayList<>(newParentAncestors);
            newDescendantAncestors.add(matchedRootId);

            // 截取移动根节点之后的部分
            int targetIndex = oldDescendantAncestors.indexOf(matchedRootId);
            if (targetIndex + 1 < oldDescendantAncestors.size()) {
                newDescendantAncestors.addAll(
                        oldDescendantAncestors.subList(targetIndex + 1, oldDescendantAncestors.size())
                );
            }

            descendant.setAncestors(newDescendantAncestors);
            entitiesToUpdate.add(descendant);
        }

        // 批量更新到 MongoDB
        tagRepository.saveAll(entitiesToUpdate);

        List<String> affectedTagIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
        affectedTagIds.addAll(targetIds);

        // 如果是被移入回收站，触发Tag下所有资源的共享小组剥夺
        if (isNodeInTrash(groupID, newParentId) == TagType.TRASH) {
            log.info("tags moved to trash. groupId={} count={} tagIds={} newParentId={} descendantCount={}",
                    groupID, targetIds.size(), summarizeIds(targetIds), newParentId, descendants.size());
            // 发布移入回收站事件
            eventPublisher.publishEvent(new TagTrashedEvent(affectedTagIds));
        } else {
            log.info("tags moved. groupId={} count={} tagIds={} newParentId={} descendantCount={}",
                    groupID, targetIds.size(), summarizeIds(targetIds), newParentId, descendants.size());
            // 正常的移动，通知所有挂在它以及它子孙节点上的资源重新计算权限
            eventPublisher.publishEvent(new TagChangedEvent(affectedTagIds, groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)));
        }
    }

    @Override
    public void deleteTags(TagDeleteRequest tagDeleteRequest, Boolean forceDelete) {
        String groupID = tagDeleteRequest.getGroupId();
        List<String> targetIds = tagDeleteRequest.getTargetTagIds().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.toList());

        List<TagEntity> targetNodes = tagRepository.findByGroupIdAndTagIdIn(groupID, targetIds);
        if (targetNodes.size() != targetIds.size()) {
            throw new ServiceException(ResourceError.TAG_NODE_NOT_FOUND);
        }

        Set<String> targetIdSet = new LinkedHashSet<>(targetIds);
        for (TagEntity targetNode : targetNodes) {
            // 同一批次不允许同时出现祖先和子孙节点
            if (targetNode.getAncestors() != null && targetNode.getAncestors().stream().anyMatch(targetIdSet::contains)) {
                throw new ServiceException(ResourceError.CANNOT_OPERATE_ANCESTOR_DESCENDANT_TAG_NODES);
            }

            // 系统级保留节点禁止删除
            if (isSystemPathTagName(targetNode.getTagName())) {
                throw new ServiceException(ResourceError.CANNOT_DELETE_SYSTEM_TAG_PATH_NODE);
            }

            // 删除 FOLDER Tag
            if (Boolean.TRUE.equals(targetNode.getIsPath())  &&
                    !Boolean.TRUE.equals(forceDelete) && // 未开启强制删除
                    isNodeInTrash(groupID, targetNode.getTagId()) != TagType.IN_TRASH // 不在回收站
            ) {
                throw new ServiceException(ResourceError.CANNOT_DELETE_TAG_PATH_NODE_DIRECTLY);
            }
        }

        // 查出即将被删除的 Tag 及其所有子孙节点的 ID 列表
        Query deleteQuery = Query.query(new Criteria().andOperator(
                Criteria.where("groupId").is(groupID),
                new Criteria().orOperator(
                        Criteria.where("_id").in(targetIds), // 本次直接要删的目标节点
                        Criteria.where("ancestors").in(targetIds) // 这些目标节点下面的所有子孙节点
                )
        ));
        List<TagEntity> deletedTags = mongoTemplate.find(deleteQuery, TagEntity.class);
        if (deletedTags.isEmpty()) return;

        // 找出本次直接删除目标里的路径节点
        Set<String> pathRootIds = targetNodes.stream()
                .filter(root -> Boolean.TRUE.equals(root.getIsPath()))
                .map(TagEntity::getTagId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> pathDeletedTagIds = new ArrayList<>();
        List<String> normalDeletedTagIds = new ArrayList<>();

        for (TagEntity deletedTag : deletedTags) {
            boolean belongsToPathRoot = pathRootIds.contains(deletedTag.getTagId())
                    || (deletedTag.getAncestors() != null && deletedTag.getAncestors().stream().anyMatch(pathRootIds::contains));
            if (belongsToPathRoot) { // 区分路径节点和普通节点
                pathDeletedTagIds.add(deletedTag.getTagId());
            } else {
                normalDeletedTagIds.add(deletedTag.getTagId());
            }
        }

        // 删除自身和所有子孙节点
        mongoTemplate.remove(deleteQuery, TagEntity.class);

        // 发布彻底删除事件
        log.info("tags deleted. groupId={} rootCount={} tagIds={} cascadeCount={}",
                groupID, targetIds.size(), summarizeIds(targetIds), deletedTags.size());
        boolean isPersonalTag = groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX);
        // 路径节点和普通节点分别发送事件
        if (!pathDeletedTagIds.isEmpty()) {
            eventPublisher.publishEvent(new TagDeletedEvent(pathDeletedTagIds, isPersonalTag, true));
        }
        if (!normalDeletedTagIds.isEmpty()) {
            eventPublisher.publishEvent(new TagDeletedEvent(normalDeletedTagIds, isPersonalTag, false));
        }
    }

    // 判断目标父节点是否为回收站，或处于回收站的子孙层级中
    public TagType isNodeInTrash(String groupId, String targetParentId) {
        // 非个人没有回收站
        if (!groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)){
            return TagType.NOT_IN_TRASH;
        }

        if ("0".equals(targetParentId) || targetParentId == null) {
            return TagType.NOT_IN_TRASH;
        }
        // 精准查出当前组位于根节点下的系统回收站
        TagEntity trashNode = tagRepository.findByGroupIdAndParentIdAndTagName(
                groupId, "0", ResourceConstants.TRASH_TAG_NAME).orElse(null);

        if (trashNode == null) {
            return TagType.NOT_IN_TRASH; // 回收站尚未初始化
        }
        if (trashNode.getTagId().equals(targetParentId)) {
            return TagType.TRASH; // 本身是回收站
        }

        // 检查是否在回收站内部的子文件夹中
        TagEntity parent = tagRepository.findByGroupIdAndTagId(groupId, targetParentId).orElse(null);
        return parent != null && parent.getAncestors() != null && parent.getAncestors().contains(trashNode.getTagId())
                ? TagType.IN_TRASH : TagType.NOT_IN_TRASH;
    }

    private static boolean isSystemPathTagName(String tagName) {
        return ResourceConstants.ROOT_TAG_NAME.equals(tagName)
                || ResourceConstants.TRASH_TAG_NAME.equals(tagName)
                || ResourceConstants.SHARED_TAG_NAME.equals(tagName);
    }

    // 内存组装树
    private List<TagTreeResponse> buildTree(List<TagTreeResponse> allNodes, String parentId) {
        return allNodes.stream()
                .filter(node -> parentId.equals(node.getParentId()))
                .peek(node -> node.setChildren(buildTree(allNodes, node.getTagId())))
                .collect(Collectors.toList());
    }

    private void afterTagNodeChanged(String groupId, String tagId, Boolean isPersonalTag) {
        // 获取当前节点 + 所有子孙节点的 ID
        List<TagEntity> descendants = tagRepository.findByGroupIdAndAncestorsContaining(groupId, tagId);
        List<String> changedTagIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
        changedTagIds.add(tagId);
        // 发布标签变更事件
        eventPublisher.publishEvent(new TagChangedEvent(changedTagIds, isPersonalTag));
    }

    @Override
    public void softRemoveAllTagByGroupId(String groupId) {
        List<TagEntity> tags = tagRepository.findByGroupId(groupId);
        if (tags.isEmpty()) {
            return;
        }
        for (TagEntity tag : tags) {
            mongoTemplate.save(tag, TAGS_TRASH_COLLECTION);
        }
        tagRepository.deleteByGroupId(groupId);
        log.info("tag tree deleted. mode=soft groupId={} count={}", groupId, tags.size());
    }

    public void hardRemoveAllTagByGroupId(String groupId) {
        List<TagEntity> tags = mongoTemplate.find(
                Query.query(Criteria.where("groupId").is(groupId)),
                TagEntity.class,
                TAGS_TRASH_COLLECTION
        );
        List<String> allTagIds = tags.stream().map(TagEntity::getTagId).collect(Collectors.toList());
        // 发布彻底删除事件
        eventPublisher.publishEvent(new TagDeletedEvent(allTagIds, false, false));
        long deletedCount = mongoTemplate.remove(
                Query.query(Criteria.where("groupId").is(groupId)),
                TAGS_TRASH_COLLECTION
        ).getDeletedCount();
        log.info("tag tree deleted. mode=hard groupId={} count={} tagIds={}",
                groupId, deletedCount, summarizeIds(allTagIds));
    }
}
