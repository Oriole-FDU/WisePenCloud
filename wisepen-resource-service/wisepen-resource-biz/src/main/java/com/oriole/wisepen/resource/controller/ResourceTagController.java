package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.resource.domain.base.TagSpaceBase;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.service.ITagService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.oriole.wisepen.resource.constant.ResourceConstants.PERSONAL_GROUP_PREFIX;

@RestController
@RequestMapping("/resource/tag")
@RequiredArgsConstructor
@CheckLogin
public class ResourceTagController {

    private final ITagService tagService;

    // 获取指定用户组的完整 Tag 树
    @GetMapping("/getTagTree")
    public R<List<TagTreeResponse>> getTagTree(@RequestParam(required = false, value = "groupId") String groupId) {
        TagSpaceBase tagSpaceBase = new TagSpaceBase(groupId);
        checkPermission(tagSpaceBase, false);
        return R.ok(tagService.getTagTree(tagSpaceBase.getGroupId()));
    }

    // 创建 Tag 节点
    @PostMapping("/addTag")
    public R<String> createTag(@Validated @RequestBody TagCreateRequest tagCreateRequest) {
        checkPermission(tagCreateRequest, true);
        return R.ok(tagService.createTag(tagCreateRequest));
    }

    // 更新 Tag (重命名、修改可见性规则等)
    @PostMapping("/changeTag")
    public R<Void> updateTag(@Validated @RequestBody TagUpdateRequest tagUpdateRequest) {
        checkPermission(tagUpdateRequest, true);
        tagService.updateTag(tagUpdateRequest);
        return R.ok();
    }

    // 拖拽移动 Tag (改变树形层级结构)
    @PostMapping("/moveTag")
    public R<Void> moveTag(@Validated @RequestBody TagMoveRequest tagMoveRequest) {
        checkPermission(tagMoveRequest, true);
        tagService.moveTag(tagMoveRequest);
        return R.ok();
    }

    // 级联删除 Tag 及其子孙节点
    @PostMapping("/removeTag")
    public R<Void> deleteTag(@Validated @RequestBody TagDeleteRequest tagDeleteRequest) {
        checkPermission(tagDeleteRequest, true);
        tagService.deleteTag(tagDeleteRequest);
        return R.ok();
    }

    private void checkPermission(TagSpaceBase tagSpaceBase, boolean isWriteOp) {
        if (StringUtils.hasText(tagSpaceBase.getGroupId())) {
            tagSpaceBase.setGroupId(PERSONAL_GROUP_PREFIX + SecurityContextHolder.getUserId()); // 个人私有空间 (p_开头)
        } else { // 正常群组
            if (isWriteOp){ // 写操作，必须是群组的 Admin 或 Owner
                SecurityContextHolder.assertGroupRole(tagSpaceBase.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
            } else { // 读操作，必须是群组成员
                SecurityContextHolder.assertInGroup(tagSpaceBase.getGroupId());
            }
        }
    }
}