package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoAdminUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileAdminUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserRegisterReqDTO;
import com.oriole.wisepen.user.api.domain.dto.req.WalletSettleCoinTradeRequest;
import com.oriole.wisepen.user.api.feign.RemoteUserMessageService;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import com.oriole.wisepen.user.api.feign.RemoteWalletService;
import com.oriole.wisepen.user.service.IDisplayService;
import com.oriole.wisepen.user.service.IGroupMemberService;
import com.oriole.wisepen.user.service.IMessageService;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.service.IWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Tag(name = "内部 - 用户", description = "供其他微服务创建和维护用户、查询用户与小组展示信息、发布站内消息并结算钱包交易")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController implements RemoteUserService, RemoteWalletService, RemoteUserMessageService {

    private final IDisplayService displayService;
    private final IGroupMemberService groupMemberService;
    private final IUserService userService;
    private final IWalletService walletService;
    private final IMessageService messageService;

    @Override
    @Operation(
            summary = "内部获取用户展示信息",
            description = """
                    - 用途：供其他微服务批量获取用户展示字段。
                    - 请求：userIds 指定待查询用户 ID 列表。
                    - 约束：调用方必须来自可信服务链路；userIds 为空时返回空映射。
                    - 处理：批量读取用户账号展示字段并按 userId 组装映射；不返回密码、钱包、资料扩展或认证状态详情。
                    - 失败：用户列表解析或查询发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回以用户 ID 为键的展示信息映射，不存在的用户不会出现在结果中。
                    """
    )
    @GetMapping("/user/getUserDisplayInfo")
    public R<Map<Long, UserDisplayBase>> getUserDisplayInfo(@RequestParam("userId") List<Long> userIds) {
        return R.ok(displayService.getUserDisplayInfoByIds(new HashSet<>(userIds)));
    }

    @Override
    @Operation(
            summary = "内部获取小组展示信息",
            description = """
                    - 用途：供其他微服务批量获取小组展示字段。
                    - 请求：groupIds 指定待查询小组 ID 列表。
                    - 约束：调用方必须来自可信服务链路；groupIds 为空时返回空映射。
                    - 处理：批量读取小组展示字段并按 groupId 组装映射；不返回成员列表、钱包额度或资源配置。
                    - 失败：小组列表解析或查询发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回以小组 ID 为键的展示信息映射，不存在的小组不会出现在结果中。
                    """
    )
    @GetMapping("/group/getGroupDisplayInfo")
    public R<Map<Long, GroupDisplayBase>> getGroupDisplayInfo(@RequestParam("groupId") List<Long> groupIds) {
        return R.ok(displayService.getGroupDisplayInfoByIds(new HashSet<>(groupIds)));
    }

    @Override
    @Operation(
            summary = "内部查询小组成员ID",
            description = """
                    - 用途：供业务微服务批量组装小组成员维度的数据。
                    - 请求：groupId 指定目标小组。
                    - 约束：调用方必须来自可信服务链路。
                    - 处理：读取目标小组的全部成员关系并返回成员 userId；不返回成员展示信息、账号信息或角色信息。
                    - 失败：无业务失败点。
                    - 响应：返回小组成员 userId 列表；没有成员时返回空列表。
                    """
    )
    @GetMapping("/group/member/listGroupMemberIds")
    public R<List<Long>> listGroupMemberIds(@RequestParam("groupId") Long groupId) {
        return R.ok(groupMemberService.listGroupMemberIds(groupId));
    }

    @Override
    @Operation(
            summary = "内部创建用户",
            description = """
                    - 用途：供系统服务执行已授权的用户创建任务。
                    - 请求：userId 可选，username 指定新账号用户名；用户账号字段和 profile 用于后续资料设置。
                    - 约束：调用方必须来自可信服务链路；username 必须符合用户规则且全局唯一；显式 userId 不能已存在；用户服务必须已配置默认初始密码。
                    - 处理：仅创建基础账号、密码密文、用户资料行和钱包，不写账号扩展字段或资料详情；不绑定邀请关系。
                    - 失败：默认密码未配置 -> UserError.USER_DEFAULT_PASSWORD_NOT_CONFIGURED；userId 重复 -> UserError.USER_ID_ALREADY_EXISTS；用户名重复 -> UserError.USERNAME_ALREADY_EXISTS。
                    - 响应：返回新创建用户的 userId。
                    """
    )
    @Log(title = "内部创建用户", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/user/createUser")
    public R<Long> createUser(@RequestBody @Valid UserRegisterReqDTO req) {
        return R.ok(userService.createUserInternal(req));
    }

    @Override
    @Operation(
            summary = "内部更新用户账号信息",
            description = """
                    - 用途：供系统服务为用户创建任务补充账号展示、认证和状态信息。
                    - 请求：userId 指定目标用户；其余字段属于 sys_user 账号信息。
                    - 约束：调用方必须来自可信服务链路；目标用户必须存在；用户名和正常状态学工号必须唯一。
                    - 处理：复用管理员账号更新规则写入账号字段，并在未传 userStatus 但有 verificationMode 时设置为 NORMAL。
                    - 失败：用户名重复 -> UserError.USERNAME_ALREADY_EXISTS；学工号冲突 -> UserError.CAMPUS_NO_ALREADY_EXISTS。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "内部更新用户账号信息", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/user/changeUserInfo")
    public R<Void> changeUserInfo(@RequestBody UserInfoAdminUpdateRequest req) {
        userService.updateUserInfoAdmin(req);
        return R.ok();
    }

    @Override
    @Operation(
            summary = "内部更新用户资料信息",
            description = """
                    - 用途：供系统服务为用户创建任务补充学籍、院系、专业、班级、学历和职称资料。
                    - 请求：userId 指定目标用户；其余字段属于 sys_user_profile 资料信息。
                    - 约束：调用方必须来自可信服务链路；目标用户资料记录必须存在。
                    - 处理：复用管理员资料更新规则写入资料字段；忽略 inviteCode，不修改账号、状态、密码或钱包。
                    - 失败：无业务失败点。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "内部更新用户资料信息", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/user/changeUserProfile")
    public R<Void> changeUserProfile(@RequestBody UserProfileAdminUpdateRequest req) {
        userService.updateProfileAdmin(req);
        return R.ok();
    }

    @Override
    @Operation(
            summary = "内部结算金币交易",
            description = """
                    - 用途：供资源交易等业务服务完成买方到卖方的金币结算。
                    - 请求：traceId 用于幂等识别交易；buyerId、sellerId 和 price 描述买方、卖方和交易金额；meta 记录业务来源信息。
                    - 约束：调用方必须来自可信服务链路；买方金币余额必须足够；traceId 已结算时接口幂等返回成功。
                    - 处理：先扣减买方金币余额，再增加卖方金币余额，并分别写入钱包交易流水；不处理资源交付或订单状态。
                    - 失败：买方金币余额不足 -> UserError.WALLET_COIN_INSUFFICIENT；金币钱包变动失败 -> UserError.WALLET_COIN_CHANGE_FAILED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/user/wallet/settleTrade")
    public R<Void> settleCoinTrade(@RequestBody @Valid WalletSettleCoinTradeRequest req) {
        walletService.settleCoinTrade(req);
        return R.ok();
    }

    @Override
    @Operation(
            summary = "内部发布站内消息",
            description = """
                    - 用途：供业务微服务向指定用户或全员投递站内消息，由用户服务维护用户收件箱状态。
                    - 请求：deliveryScope=DIRECT 时 receiverUserIds 指定接收用户；deliveryScope=ALL_USERS 时 receiverUserIds 可为空且 messageType 必须为 SYSTEM；title、content 描述消息内容；sourceService 和 bizTraceId 用于业务幂等；jumpUrl 和 extra 为可选展示扩展。
                    - 约束：调用方必须来自可信服务链路；DIRECT 消息必须提供接收用户；ALL_USERS 仅支持系统消息；sourceService 与 bizTraceId 组合应在调用方业务内唯一。
                    - 处理：按 sourceService 和 bizTraceId 幂等创建消息主体；DIRECT 消息批量补齐接收用户收件箱记录；ALL_USERS 消息只创建消息主体，用户查询消息中心时再批量同步个人收件箱；不发送邮件、短信或实时 WebSocket 推送。
                    - 失败：DIRECT 消息未提供接收用户 -> UserError.MESSAGE_RECEIVER_REQUIRED；投递范围与消息类型组合无效 -> UserError.MESSAGE_DELIVERY_SCOPE_INVALID；幂等键已存在但投递范围或消息类型不一致 -> UserError.MESSAGE_PUBLISH_CONFLICT。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "内部发布站内消息", businessType = BusinessType.INSERT, isSaveResponseData = false)
    @PostMapping("/user/message/publishMessage")
    public R<Void> publishMessage(@RequestBody @Valid MessagePublishRequest req) {
        messageService.publishMessage(req);
        return R.ok();
    }
}
