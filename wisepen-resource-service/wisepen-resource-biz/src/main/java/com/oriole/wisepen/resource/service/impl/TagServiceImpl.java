package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
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
import com.oriole.wisepen.resource.exception.ResPermissionErrorCode;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.resource.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.resource.constant.ResourceConstants.TAGS_TRASH_COLLECTION;
import static com.oriole.wisepen.resource.exception.ResPermissionErrorCode.CANNOT_SET_VISIBILITY;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements ITagService {

    private final TagRepository tagRepository;
    private final MongoTemplate mongoTemplate;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String createTag(TagCreateRequest tagCreateRequest) {
        String groupID = tagCreateRequest.getGroupId();
        String parentId = tagCreateRequest.getParentId();
        String tagName = tagCreateRequest.getTagName();

        // 禁止在回收站及其子目录下创建任何新节点
        if (isNodeInTrash(groupID, parentId) != TagType.NOT_IN_TRASH) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_OPERATE_IN_TRASH);
        }

        // 禁止建立系统级保留节点 Tag
        if (ResourceConstants.ROOT_TAG_NAME.equals(tagName) || ResourceConstants.TRASH_TAG_NAME.equals(tagName)) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_USE_SYSTEM_RESERVED_NAME);
        }

        // 校验同级重名
        tagRepository.findByGroupIdAndParentIdAndTagName(groupID, parentId, tagName)
                .ifPresent(t -> { throw new ServiceException(ResPermissionErrorCode.TAG_NAME_DUPLICATE); });

        TagEntity entity = new TagEntity();

        BeanUtil.copyProperties(tagCreateRequest, entity);
        if (tagCreateRequest.getGrantedActions() != null) {
            entity.setGrantedActionsMask(ResourceAction.actionsToPermissionCode(tagCreateRequest.getGrantedActions()));
        }

        if (groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)){
            // 个人组标签不能设置标签权限
            entity.setAclGrantMode(null);
            entity.setResourceMountMode(null);
            entity.setAclGrantSpecifiedUsers(null);
            entity.setResourceMountSpecifiedUsers(null);
            entity.setGrantedActionsMask(null);
        }

        // 计算祖先数组 (核心逻辑)
        if (parentId != null && !"0".equals(parentId)) {
            TagEntity parent = tagRepository.findByGroupIdAndTagId(groupID, parentId)
                    .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.PARENT_TAG_NOT_FOUND));

//            // 跨类型校验，FOLDER Tag只能在FOLDER Tag下创建，Normal Tag只能在Normal Tag下创建
//            if (!Objects.equals(parent.getIsPath(), entity.getIsPath())) {
//                throw new ServiceException(ResPermissionErrorCode.CROSS_TYPE_OPERATION_NOT_ALLOWED);
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
        log.info("tag created groupId={} parentId={} tagId={} isPath={}",
                saved.getGroupId(), saved.getParentId(), saved.getTagId(), saved.getIsPath());
        return saved.getTagId();
    }

    @Override
    public List<TagTreeResponse> getTagTree(String groupId) {
        // 一次性查出该组所有节点，避免 N+1 查询问题
        List<TagEntity> allTags = tagRepository.findByGroupId(groupId);

        if (groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
            boolean hasRoot = false;
            boolean hasTrash = false;

            for (TagEntity tag : allTags) {
                if ("0".equals(tag.getParentId())) {
                    if (ResourceConstants.ROOT_TAG_NAME.equals(tag.getTagName())) hasRoot = true;
                    if (ResourceConstants.TRASH_TAG_NAME.equals(tag.getTagName())) hasTrash = true;
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

            if (initialized) {
                log.info("personalSpace created groupId={} nodes=root,trash", groupId);
            }
        }

        // 转换为 DTO
        List<TagTreeResponse> tagTreeResponseList = allTags.stream().map(entity -> {
            TagTreeResponse tagTreeResponse = new TagTreeResponse();
            BeanUtil.copyProperties(entity, tagTreeResponse);
            tagTreeResponse.setChildren(new ArrayList<>());
            return tagTreeResponse;
        }).collect(Collectors.toList());

        // 在内存中组装树状结构 (比在 DB 中递归查快得多)
        return buildTree(tagTreeResponseList, "0");
    }

    // --- 更新 Tag (Update) ---
    @Override
    public void updateTag(TagUpdateRequest tagUpdateRequest) {
        String groupID = tagUpdateRequest.getGroupId();
        String targetId = tagUpdateRequest.getTargetTagId();

        // 严禁修改处于回收站中的任何节点属性
        if (isNodeInTrash(groupID, targetId) == TagType.IN_TRASH) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_OPERATE_IN_TRASH);
        }

        TagEntity entity = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND));

        String newName = tagUpdateRequest.getTagName();

        if (newName != null && !newName.equals(entity.getTagName())) {
            // 名称变动时，校验在当前父节点下是否重名
            tagRepository.findByGroupIdAndParentIdAndTagName(groupID, entity.getParentId(), newName)
                    .ifPresent(t -> { throw new ServiceException(ResPermissionErrorCode.TAG_NAME_DUPLICATE); });
        }

        // 禁止修改系统级保留 Tag
        if (ResourceConstants.ROOT_TAG_NAME.equals(entity.getTagName()) || ResourceConstants.TRASH_TAG_NAME.equals(entity.getTagName())) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_MODIFY_SYSTEM_NODE);
        }

        // 禁止改名为系统级保留节点 Tag
        if (ResourceConstants.ROOT_TAG_NAME.equals(newName) || ResourceConstants.TRASH_TAG_NAME.equals(newName)) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_USE_SYSTEM_RESERVED_NAME);
        }

        // 是否有权限变更
        boolean isPermissionChanged = false;
        boolean isMountModeChanged = false;
        boolean isMountListChanged = false;
        if (tagUpdateRequest.getAclGrantMode() != null && !tagUpdateRequest.getAclGrantMode().equals(entity.getAclGrantMode())) {
            isPermissionChanged = true;
        }
        if (tagUpdateRequest.getResourceMountMode() != null && !tagUpdateRequest.getResourceMountMode().equals(entity.getResourceMountMode())) {
            isMountModeChanged = true;
        }
        if (tagUpdateRequest.getResourceMountSpecifiedUsers() != null && !tagUpdateRequest.getResourceMountSpecifiedUsers().equals(entity.getResourceMountSpecifiedUsers())) {
            isMountListChanged = true;
        }
        if (tagUpdateRequest.getAclGrantSpecifiedUsers() != null && !tagUpdateRequest.getAclGrantSpecifiedUsers().equals(entity.getAclGrantSpecifiedUsers())) {
            isPermissionChanged = true;
        }
        if (tagUpdateRequest.getGrantedActions() != null && ResourceAction.actionsToPermissionCode(tagUpdateRequest.getGrantedActions()) != entity.getGrantedActionsMask()) {
            isPermissionChanged = true;
        }

        if (groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX) && (isPermissionChanged || isMountModeChanged || isMountListChanged)){
            throw new ServiceException(CANNOT_SET_VISIBILITY); // 个人组标签不能设置标签权限
        }

        boolean nameChanged = newName != null && !newName.equals(entity.getTagName());

        // 更新基本信息和权限策略
        tagUpdateRequest.setIsPath(null); // IsPath始终不允许修改
        BeanUtil.copyProperties(tagUpdateRequest, entity, CopyOptions.create().ignoreNullValue());
        entity.setGrantedActionsMask(ResourceAction.actionsToPermissionCode(tagUpdateRequest.getGrantedActions()));

        tagRepository.save(entity);

        log.info("tag updated groupId={} tagId={} nameChanged={} permissionChanged={}",
                groupID, targetId, nameChanged, isPermissionChanged);

        if (isPermissionChanged) {
            log.info("tagPermission changed groupId={} tagId={} mode={}",
                    groupID, targetId, entity.getAclGrantMode());
            // 通知所有挂在它以及它子孙节点上的资源重新计算权限
            // 个人组标签不能设置标签权限，已经提前抛出错误
            afterTagNodeChanged(groupID, targetId, false);
        }
    }

    @Override
    public void moveTag(TagMoveRequest tagMoveRequest) {
        String groupID = tagMoveRequest.getGroupId();
        String targetId = tagMoveRequest.getTargetTagId();
        String newParentId = tagMoveRequest.getNewParentId() == null ? "0" : tagMoveRequest.getNewParentId();

        // 严禁向回收站内部节点移动Tag
        // 用户可把外面的节点移入回收站
        if (isNodeInTrash(groupID, newParentId) == TagType.IN_TRASH) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_OPERATE_IN_TRASH);
        }

        // 目标父节点不能是自己
        if (newParentId.equals(targetId)) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_MOVE_TO_SELF);
        }

        // 获取当前节点
        TagEntity targetNode = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND));

        String oldParentId = targetNode.getParentId();

        // 如果父节点没变直接返回
        if (newParentId.equals(targetNode.getParentId())) {
            return;
        }

        // 系统级保留节点禁止移动位置
        if (ResourceConstants.ROOT_TAG_NAME.equals(targetNode.getTagName()) || ResourceConstants.TRASH_TAG_NAME.equals(targetNode.getTagName())) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_MODIFY_SYSTEM_NODE);
        }

        // 移动到新位置前，校验目标目录下是否有同名节点
        tagRepository.findByGroupIdAndParentIdAndTagName(groupID, newParentId, targetNode.getTagName())
                .ifPresent(t -> {
                    // 回收站例外，允许有同名节点
                    if (isNodeInTrash(groupID, newParentId) !=  TagType.TRASH) {
                        throw new ServiceException(ResPermissionErrorCode.TAG_NAME_DUPLICATE);
                    }
                });

        // 获取目标父节点 & 防环形依赖校验
        // 绝对不能把一个节点拖拽到它自己的子孙节点下面，否则会形成死循环树
        List<String> newParentAncestors = new ArrayList<>();
        if (!"0".equals(newParentId)) {
            TagEntity newParentNode = tagRepository.findByGroupIdAndTagId(groupID, newParentId)
                    .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.PARENT_TAG_NOT_FOUND));

            // 跨类型移动校验，防止将 Normal Tag 拖入 FOLDER Tag 或将 FOLDER Tag 拖入 Normal Tag
            if (!Objects.equals(targetNode.getIsPath(), newParentNode.getIsPath())) {
                throw new ServiceException(ResPermissionErrorCode.CROSS_TYPE_OPERATION_NOT_ALLOWED);
            }

            // 目标父节点不能是自己的子孙节点
            if (newParentNode.getAncestors() != null && newParentNode.getAncestors().contains(targetId)) {
                throw new ServiceException(ResPermissionErrorCode.CANNOT_MOVE_TO_DESCENDANT);
            }

            if (newParentNode.getAncestors() != null) {
                newParentAncestors.addAll(newParentNode.getAncestors());
            }
            newParentAncestors.add(newParentId); // 新父节点的 ancestors + 新父节点自身
        }

        // 更新当前被拖拽的节点
        targetNode.setParentId(newParentId);
        targetNode.setAncestors(newParentAncestors);

        // 用于批量保存的列表
        List<TagEntity> entitiesToUpdate = new ArrayList<>();
        entitiesToUpdate.add(targetNode);

        // 查询当前节点的所有子孙节点
        List<TagEntity> descendants = tagRepository.findByGroupIdAndAncestorsContaining(groupID, targetId);

        // 遍历并重算所有子孙节点的 ancestors
        for (TagEntity descendant : descendants) {
            List<String> oldDescendantAncestors = descendant.getAncestors();

            // 新的祖先路径 = targetNode 的新祖先 + targetNode 本身 + (该子节点原来在 targetNode 下面的路径)
            List<String> newDescendantAncestors = new ArrayList<>(newParentAncestors);
            newDescendantAncestors.add(targetId);

            // 截取 targetNode 之后的部分
            int targetIndex = oldDescendantAncestors.indexOf(targetId);
            if (targetIndex != -1 && targetIndex + 1 < oldDescendantAncestors.size()) {
                newDescendantAncestors.addAll(
                        oldDescendantAncestors.subList(targetIndex + 1, oldDescendantAncestors.size())
                );
            }

            descendant.setAncestors(newDescendantAncestors);
            entitiesToUpdate.add(descendant);
        }

        // 批量更新到 MongoDB
        tagRepository.saveAll(entitiesToUpdate);

        // 如果是被移入回收站，触发Tag下所有资源的共享小组剥夺
        if (isNodeInTrash(groupID, newParentId) == TagType.TRASH) {
            log.info("tag moved groupId={} tagId={} oldParentId={} newParentId={}(trash) descendantCount={}",
                    groupID, targetId, oldParentId, newParentId, descendants.size());

            List<String> affectedTagIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
            affectedTagIds.add(targetId);
            // 发布移入回收站事件
            eventPublisher.publishEvent(new TagTrashedEvent(affectedTagIds));
        } else {
            log.info("tag moved groupId={} tagId={} oldParentId={} newParentId={} descendantCount={}",
                    groupID, targetId, oldParentId, newParentId, descendants.size());
            // 正常的移动，通知所有挂在它以及它子孙节点上的资源重新计算权限
            afterTagNodeChanged(groupID, targetId, groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX));
        }
    }

    @Override
    public void deleteTag(TagDeleteRequest tagDeleteRequest, Boolean forceDelete) {
        String groupID = tagDeleteRequest.getGroupId();
        String targetId = tagDeleteRequest.getTargetTagId();

        TagEntity targetNode = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND));

        // 系统级保留节点禁止删除
        if (ResourceConstants.ROOT_TAG_NAME.equals(targetNode.getTagName()) || ResourceConstants.TRASH_TAG_NAME.equals(targetNode.getTagName())) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_DELETE_SYSTEM_NODE);
        }

        // 删除 FOLDER Tag
        if (Boolean.TRUE.equals(targetNode.getIsPath())  &&
                !Boolean.TRUE.equals(forceDelete) && // 未开启强制删除
                isNodeInTrash(groupID, targetId) != TagType.IN_TRASH // 不在回收站
        ) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_DELETE_PATH_DIRECTLY);
        }

        // 查出即将被删除的 Tag 及其所有子孙节点的 ID 列表
        List<TagEntity> descendants = tagRepository.findByGroupIdAndAncestorsContaining(groupID, targetId);
        List<String> deletedTagIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
        deletedTagIds.add(targetId); // 加上当前要删的节点自身

        // 删除自身
        tagRepository.delete(targetNode);
        // 依靠 ancestors 数组，一键删除所有子孙节点
        tagRepository.deleteByGroupIdAndAncestorsContaining(groupID, targetId);

        // 发布彻底删除事件
        log.info("tag deleted groupId={} tagId={} cascadeCount={} isPath={}",
                groupID, targetId, deletedTagIds.size(), targetNode.getIsPath());
        eventPublisher.publishEvent(new TagDeletedEvent(deletedTagIds, groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX), targetNode.getIsPath()));
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
        log.info("tagTree deleted mode=soft groupId={} count={}", groupId, tags.size());
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
        log.info("tagTree deleted mode=hard groupId={} count={} tagIds={}",
                groupId, deletedCount, summarizeIds(allTagIds));
    }
}