package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.exception.ResPermissionErrorCode;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.ITagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.oriole.wisepen.resource.exception.ResPermissionErrorCode.CANNOT_SET_VISIBILITY;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements ITagService {

    private final TagRepository tagRepository;
    private final IResourceService permissionService;

    @Override
    public String createTag(TagCreateRequest tagCreateRequest) {
        String groupID = tagCreateRequest.getGroupId();
        String parentId = tagCreateRequest.getParentId();

        TagEntity entity = new TagEntity();
        BeanUtil.copyProperties(tagCreateRequest, entity);
        entity.setCreateTime(new Date());

        if (groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)){
            // 个人组标签不能设置标签权限
            entity.setVisibilityMode(null);
            entity.setSpecifiedUsers(null);
        }

        // 计算祖先数组 (核心逻辑)
        if (parentId != null && !"0".equals(parentId)) {
            TagEntity parent = tagRepository.findByGroupIdAndTagId(groupID, parentId)
                    .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.PARENT_TAG_NOT_FOUND));

            List<String> newAncestors = new ArrayList<>(parent.getAncestors() == null ?
                    Collections.emptyList() : parent.getAncestors());
            newAncestors.add(parent.getTagId());
            entity.setAncestors(newAncestors);
        } else {
            entity.setParentId("0");
            entity.setAncestors(new ArrayList<>());
        }

        return tagRepository.save(entity).getTagId();
    }

    @Override
    public List<TagTreeResponse> getTagTree(String groupId) {
        // 一次性查出该组所有节点，避免 N+1 查询问题
        List<TagEntity> allTags = tagRepository.findByGroupId(groupId);

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

        TagEntity entity = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND));

        // 是否有权限变更
        boolean isPermissionChanged = false;
        if (tagUpdateRequest.getVisibilityMode() != null && !tagUpdateRequest.getVisibilityMode().equals(entity.getVisibilityMode())) {
            isPermissionChanged = true;
        }
        if (tagUpdateRequest.getSpecifiedUsers() != null && !tagUpdateRequest.getSpecifiedUsers().equals(entity.getSpecifiedUsers())) {
            isPermissionChanged = true;
        }

        if (groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX) && isPermissionChanged){
            throw new ServiceException(CANNOT_SET_VISIBILITY); // 个人组标签不能设置标签权限
        }

        // 更新基本信息和权限策略
        BeanUtil.copyProperties(tagUpdateRequest, entity, CopyOptions.create().ignoreNullValue());
        entity.setUpdateTime(new Date());

        tagRepository.save(entity);

        if (isPermissionChanged) {
            // 通知所有挂在它以及它子孙节点上的资源重新计算权限
            afterTagNodeChanged(groupID, targetId);
        }
    }

    @Override
    public void moveTag(TagMoveRequest tagMoveRequest) {
        String groupID = tagMoveRequest.getGroupId();
        String targetId = tagMoveRequest.getTargetTagId();
        String newParentId = tagMoveRequest.getNewParentId() == null ? "0" : tagMoveRequest.getNewParentId();

        // 目标父节点不能是自己
        if (newParentId.equals(targetId)) {
            throw new ServiceException(ResPermissionErrorCode.CANNOT_MOVE_TO_SELF);
        }

        // 获取当前节点
        TagEntity targetNode = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND));

        // 如果父节点没变直接返回
        if (newParentId.equals(targetNode.getParentId())) {
            return;
        }

        // 获取目标父节点 & 防环形依赖校验
        // 绝对不能把一个节点拖拽到它自己的子孙节点下面，否则会形成死循环树
        List<String> newParentAncestors = new ArrayList<>();
        if (!"0".equals(newParentId)) {
            TagEntity newParentNode = tagRepository.findByGroupIdAndTagId(groupID, newParentId)
                    .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.PARENT_TAG_NOT_FOUND));

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
        targetNode.setUpdateTime(new java.util.Date());

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
            descendant.setUpdateTime(new java.util.Date());
            entitiesToUpdate.add(descendant);
        }

        // 批量更新到 MongoDB
        tagRepository.saveAll(entitiesToUpdate);

        // 通知所有挂在它以及它子孙节点上的资源重新计算权限
        afterTagNodeChanged(groupID, targetId);
    }

    @Override
    public void deleteTag(TagDeleteRequest tagDeleteRequest) {
        String groupID = tagDeleteRequest.getGroupId();
        String targetId = tagDeleteRequest.getTargetTagId();

        TagEntity targetNode = tagRepository.findByGroupIdAndTagId(groupID, targetId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND));

        // 查出即将被删除的 Tag 及其所有子孙节点的 ID 列表
        List<TagEntity> descendants = tagRepository.findByGroupIdAndAncestorsContaining(groupID, targetId);
        List<String> deletedTagIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
        deletedTagIds.add(targetId); // 加上当前要删的节点自身

        // 删除自身
        tagRepository.delete(targetNode);
        // 依靠 ancestors 数组，一键删除所有子孙节点
        tagRepository.deleteByGroupIdAndAncestorsContaining(groupID, targetId);

        // 资源解绑被删除的Tag（在非个人Tag时，该方法会触发资源权限的重新计算）
        permissionService.afterTagNodeDeleted(deletedTagIds, groupID.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX));
    }

    // --- 内存组装树 ---
    private List<TagTreeResponse> buildTree(List<TagTreeResponse> allNodes, String parentId) {
        return allNodes.stream()
                .filter(node -> parentId.equals(node.getParentId()))
                .peek(node -> node.setChildren(buildTree(allNodes, node.getTagId())))
                .collect(Collectors.toList());
    }

    private void afterTagNodeChanged(String groupId, String tagId) {
        // 获取当前节点 + 所有子孙节点的 ID
        List<TagEntity> descendants = tagRepository.findByGroupIdAndAncestorsContaining(groupId, tagId);
        List<String> changedTagIds = descendants.stream().map(TagEntity::getTagId).collect(Collectors.toList());
        changedTagIds.add(tagId);
        permissionService.afterTagNodeChanged(changedTagIds);
    }
}