package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.resource.domain.dto.*;

import java.util.List;

public interface ITagService {

    // 创建 Tag 节点
    String createTag(TagCreateRequest tagCreateRequest);

    // 获取指定用户组下的完整 Tag 树
    List<TagTreeResponse> getTagTree(String groupId);

    // 拖拽/移动 Tag 节点 (核心难点：维护整棵子树的 ancestors)
    void moveTag(TagMoveRequest tagMoveRequest);

    // 更新 Tag 信息及权限配置
    void updateTag(TagUpdateRequest tagUpdateRequest);

    // 级联删除 Tag 及其所有子孙节点
    void deleteTag(TagDeleteRequest tagDeleteRequest);
}