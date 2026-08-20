package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.resource.domain.dto.req.TagCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagMoveRequest;
import com.oriole.wisepen.resource.domain.dto.req.TagUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.TagTreeResponse;

import java.util.List;

public interface ITagService {

    // 创建 Tag 节点
    String createTag(TagCreateRequest tagCreateRequest, String creatorUserId);

    // 获取指定用户组下的完整 Tag 树
    List<TagTreeResponse> getTagTree(String groupId);

    // 确保个人空间的系统路径节点存在
    void ensurePersonalSystemTags(String groupId);

    // 批量拖拽/移动多个 Tag 节点 (核心难点：维护多棵子树的 ancestors)
    void moveTags(TagMoveRequest tagMoveRequest);

    // 更新 Tag 信息及权限配置
    void updateTag(TagUpdateRequest tagUpdateRequest);

    // 批量级联删除多个 Tag 及其所有子孙节点
    void deleteTags(TagDeleteRequest tagDeleteRequest, Boolean forceDelete);

    // 判断节点是否属于回收站
    enum TagType { IN_TRASH, TRASH, NOT_IN_TRASH }
    TagType isNodeInTrash(String groupId, String targetParentId);

    // 小组解散时软删除该组下所有 Tag
    void softRemoveAllTagByGroupId(String groupId);

    // 硬删除该组下所有 Tag（必须先执行软删除）
    void hardRemoveAllTagByGroupId(String groupId);
}
