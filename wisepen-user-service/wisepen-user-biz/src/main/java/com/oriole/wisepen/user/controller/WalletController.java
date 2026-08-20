package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.user.api.domain.dto.req.WalletRedeemVoucherRequest;
import com.oriole.wisepen.user.api.domain.dto.req.WalletTransferTokenRequest;
import com.oriole.wisepen.user.api.domain.dto.res.WalletDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.WalletTransactionRecordResponse;
import com.oriole.wisepen.user.api.enums.WalletBusinessType;
import com.oriole.wisepen.user.api.enums.WalletPayerType;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import com.oriole.wisepen.user.service.IWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户钱包", description = "用户钱包信息点、兑换码、转账与流水查询")
@RestController
@RequestMapping("/user/wallet")
@RequiredArgsConstructor
@Validated
@CheckRole
public class WalletController {

    private final IWalletService walletService;

    @Operation(
            summary = "获取用户钱包信息",
            description = """
                    - 用途：查询当前用户个人钱包的信息点和金币余额。
                    - 请求：无需请求参数，目标钱包来自当前认证上下文。
                    - 约束：当前用户必须已登录，且用户钱包记录必须存在。
                    - 处理：读取当前用户钱包记录并转换为钱包详情响应；不刷新消费流水或余额状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回当前用户钱包详情。
                    """
    )
    @GetMapping("/getUserWalletInfo")
    public R<WalletDetailResponse> getUserWalletInfo() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(walletService.getUserWalletInfo(userId));
    }

    @Operation(
            summary = "兑换信息点券",
            description = """
                    - 用途：当前用户使用兑换码为个人钱包充值信息点。
                    - 请求：voucherCode 指定待兑换的信息点券。
                    - 约束：当前用户必须已登录；兑换码必须存在、未使用且未过期。
                    - 处理：以原子条件将兑换券标记为已使用，并向用户个人信息点余额充值，同时写入钱包流水。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；兑换码不存在 -> UserError.WALLET_VOUCHER_NOT_FOUND；兑换码已使用或并发消费失败 -> UserError.WALLET_VOUCHER_INVALID；兑换码已过期 -> UserError.WALLET_VOUCHER_EXPIRED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/redeemVoucher")
    public R<Void> redeemVoucher(@RequestBody WalletRedeemVoucherRequest req) {
        Long userId = SecurityContextHolder.getUserId();
        walletService.redeemVoucher(userId, req.getVoucherCode());
        return R.ok();
    }

    @Operation(
            summary = "转移用户与小组信息点",
            description = """
                    - 用途：小组 OWNER 在个人钱包和高级小组钱包之间转移信息点。
                    - 请求：groupId 指定目标小组；tokenCount 为转移数量；tokenTransferType 决定转入小组或转回个人。
                    - 约束：当前用户必须是目标小组 OWNER；目标小组必须是高级小组；转出方余额必须足够。
                    - 处理：按转移方向分别扣减转出方、增加转入方余额，写入对应钱包流水，并按余额状态更新聊天熔断缓存。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是 OWNER -> PermissionError.PERMISSION_DENIED；小组不存在 -> UserError.GROUP_NOT_EXIST；普通小组不允许配置钱包 -> UserError.CANNOT_CONFIGURE_GROUP_WALLET_QUOTA；转出方余额不足 -> UserError.WALLET_TOKEN_LIMIT_BELOW_USED。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/transferTokenBetweenGroupAndUser")
    public R<Void> transferTokenBetweenGroupAndUser(@RequestBody @Valid WalletTransferTokenRequest req) {
        Long userId = SecurityContextHolder.getUserId();
        SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
        walletService.transferTokenBetweenGroupAndUser(userId, req);
        return R.ok();
    }

    @Operation(
            summary = "分页查询钱包流水",
            description = """
                    - 用途：查询当前用户个人钱包或其管理小组钱包的交易流水。
                    - 请求：groupId 为空时查询个人钱包流水；groupId 非空时查询小组钱包流水；walletTransactionType 和 walletBusinessType 用于过滤流水类型；page 和 size 控制分页。
                    - 约束：当前用户必须已登录；查询小组钱包时当前用户必须是该小组 OWNER。
                    - 处理：按付款主体、业务类型和交易类型分页查询流水，并补充操作人展示信息。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是小组 OWNER -> PermissionError.PERMISSION_DENIED。
                    - 响应：返回分页钱包流水和总数。
                    """
    )
    @GetMapping("/listTransactions")
    public R<PageR<WalletTransactionRecordResponse>> listTransactions(
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "walletTransactionType", required = false) WalletTransactionType walletTransactionType,
            @RequestParam(value = "walletBusinessType", required = false) WalletBusinessType walletBusinessType,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size
    ) {
        WalletPayerType payerType;
        Long payerId;
        if (groupId == null) {
            payerType = WalletPayerType.USER;
            payerId = SecurityContextHolder.getUserId();
        } else {
            payerType = WalletPayerType.GROUP;
            payerId = groupId;
            SecurityContextHolder.assertGroupRole(groupId, GroupRoleType.OWNER);
        }
        return R.ok(walletService.listTransactions(payerType, payerId, walletTransactionType, walletBusinessType, page, size));
    }
}
