package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.req.GroupResConfigUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.GroupResConfigResponse;
import com.oriole.wisepen.resource.service.IGroupResService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小组资源配置", description = "小组文件组织模式（Folder / Tag）的查询与设置")
@RestController
@RequestMapping("/resource/groupConfig")
@RequiredArgsConstructor
@CheckRole
public class GroupResConfigController {

    private final IGroupResService groupResService;

    @Operation(
            summary = "获取小组资源配置",
            description = """
                    - 用途：查询指定小组的资源组织模式和默认成员动作权限配置。
                    - 请求：groupId 指定目标小组。
                    - 约束：当前用户必须已登录且属于目标小组。
                    - 处理：读取小组资源配置；查不到配置时由服务层返回默认 FOLDER 模式。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不属于目标小组 -> PermissionError.PERMISSION_DENIED。
                    - 响应：返回小组资源配置。
                    """
    )
    @GetMapping("/getConfig")
    public R<GroupResConfigResponse> getConfig(@RequestParam("groupId") String groupId) {
        SecurityContextHolder.assertInGroup(Long.parseLong(groupId));
        return R.ok(groupResService.getGroupResConfig(groupId));
    }

    @Operation(
            summary = "设置小组资源配置",
            description = """
                    - 用途：小组管理员设置小组资源的组织模式和默认权限策略。
                    - 请求：请求体携带 groupId、文件组织模式和默认成员动作权限配置。
                    - 约束：当前用户必须是目标小组 OWNER 或 ADMIN。
                    - 处理：首次调用时创建配置记录，后续调用更新已有配置；不直接迁移已存在资源的标签绑定。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是小组 OWNER/ADMIN -> PermissionError.PERMISSION_DENIED；小组资源模式不允许从 TAG 改为 FOLDER -> ResourceError.CANNOT_CHANGE_FILE_ORG_LOGIC_FROM_TAG_TO_FOLDER。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "修改小组资源配置", businessType = BusinessType.UPDATE)
    @PostMapping("/changeConfig")
    public R<Void> upsertConfig(@RequestBody GroupResConfigUpdateRequest req) {
        SecurityContextHolder.assertGroupRole(Long.parseLong(req.getGroupId()), GroupRoleType.OWNER, GroupRoleType.ADMIN);
        groupResService.upsertGroupResConfig(req);
        return R.ok();
    }
}
