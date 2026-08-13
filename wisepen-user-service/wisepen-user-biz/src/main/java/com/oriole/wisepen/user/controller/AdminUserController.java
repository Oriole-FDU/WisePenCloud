package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@Tag(name = "管理员 - 用户", description = "管理员查询用户、维护用户状态、资料与密码")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
public class AdminUserController {

    private final IUserService userService;

    @Operation(
            summary = "分页查询用户",
            description = """
                    - 用途：管理员按关键字、账号状态和身份类型筛选用户列表。
                    - 请求：keyword 支持真实姓名模糊匹配，或学工号、用户名、用户 ID 精确匹配；userStatus 和 identityType 为可选过滤条件；page 和 size 控制分页。
                    - 约束：当前操作者必须具备管理员身份；keyword 作为用户 ID 匹配时需可转换为数字。
                    - 处理：查询未逻辑删除用户并按创建时间倒序分页，返回前清空密码字段；不返回用户资料扩展详情。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：返回分页用户账号列表和总数。
                    """
    )
    @GetMapping("/getUserList")
    @Log(title = "管理员查询用户列表", businessType = BusinessType.SELECT)
    public R<PageR<UserEntity>> getUserList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus userStatus,
            @RequestParam(required = false) IdentityType identityType
    ) {
        return R.ok(userService.getUserListAdmin(page, size, keyword, userStatus, identityType));
    }

    @Operation(
            summary = "获取用户资料详情",
            description = """
                    - 用途：管理员查看指定用户的资料域详情。
                    - 请求：userId 指定目标用户。
                    - 约束：当前操作者必须具备管理员身份；目标用户资料记录必须存在。
                    - 处理：读取 sys_user_profile 对应资料记录；不返回用户密码、钱包余额或小组成员信息。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：返回目标用户资料实体。
                    """
    )
    @GetMapping("/getUserInfo")
    @Log(title = "管理员获取用户详情", businessType = BusinessType.SELECT)
    public R<UserProfileEntity> getUserInfo(@RequestParam("userId") Long userId) {
        return R.ok(userService.getUserDetailInfoAdmin(userId));
    }

    @Operation(
            summary = "更新用户学籍资料",
            description = """
                    - 用途：管理员修正指定用户的性别、学校、院系、专业、班级、入学年份、学历层次和职称资料。
                    - 请求：userId 指定被维护用户；其余字段属于用户资料域，对应 sys_user_profile；未传字段不表达业务变更意图。
                    - 约束：当前操作者必须具备管理员身份；目标用户资料记录必须存在。
                    - 处理：更新用户资料域，不修改账号、昵称、真实姓名、头像、身份类型、认证方式、账号状态或密码；学生资料与教师资料的互斥清理不由本接口自动完成。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/changeUserProfile")
    @Log(title = "管理员更新用户资料", businessType = BusinessType.UPDATE)
    public R<Void> updateUserProfile(@RequestBody UserProfileAdminUpdateRequest req) {
        userService.updateProfileAdmin(req);
        return R.ok();
    }

    @Operation(
            summary = "更新用户账号信息",
            description = """
                    - 用途：管理员维护指定用户的账号展示信息、认证信息、身份类型和账号状态。
                    - 请求：userId 指定被维护用户；username、campusNo、email、mobile、verificationMode、userStatus、identityType 等字段属于用户账号域，对应 sys_user。
                    - 约束：当前操作者必须具备管理员身份；目标用户必须存在；username 在全量用户中必须唯一；正常状态用户的 campusNo 必须唯一。
                    - 处理：更新用户账号域；当 identityType 发生变化时，同步清理不适用于新身份的资料字段：切换为学生时清空职称，切换为教师时清空专业、班级、入学年份和学历层次。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED；用户名重复 -> UserError.USERNAME_ALREADY_EXISTS；学工号冲突 -> UserError.CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/changeUserInfo")
    @Log(title = "管理员更新用户信息", businessType = BusinessType.UPDATE)
    public R<Void> updateUserInfo(@RequestBody UserInfoAdminUpdateRequest req) {
        userService.updateUserInfoAdmin(req);
        return R.ok();
    }

    @Operation(
            summary = "重置用户密码",
            description = """
                    - 用途：管理员为指定用户重置登录密码。
                    - 请求：userId 指定目标用户；newPassword 为空时使用系统默认密码。
                    - 约束：当前操作者必须具备管理员身份；目标用户必须存在。
                    - 处理：更新目标用户密码密文，并删除该用户已有登录会话，强制目标用户重新登录。
                    - 失败：当前操作者不是管理员 -> PermissionError.UNAUTHORIZED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/resetPassword")
    @Log(title = "管理员重置用户密码", businessType = BusinessType.UPDATE)
    public R<Void> resetPassword(@RequestBody AuthPwdAdminResetRequest req) {
        userService.resetPasswordAdmin(req);
        return R.ok();
    }
}
