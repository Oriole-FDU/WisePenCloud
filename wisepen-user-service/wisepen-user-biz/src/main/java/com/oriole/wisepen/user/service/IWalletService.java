package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.user.api.domain.dto.req.WalletSettleCoinTradeRequest;
import com.oriole.wisepen.user.api.domain.dto.req.WalletTransferTokenRequest;
import com.oriole.wisepen.user.api.domain.dto.res.WalletDetailResponse;
import com.oriole.wisepen.user.api.enums.WalletBusinessType;
import com.oriole.wisepen.user.api.enums.WalletPayerType;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberTokenLimitUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberTokenDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.WalletTransactionRecordResponse;
import com.oriole.wisepen.user.api.domain.mq.TokenConsumptionMessage;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import com.oriole.wisepen.user.api.enums.TokenTransferType;

import java.util.List;

public interface IWalletService {

    // 消耗 Token
    void consumptionToken(TokenConsumptionMessage message);

    // 改变小组 Token 余额
    void changeGroupTokenBalance(Long groupId, Long operator, Integer changedToken, WalletTransactionType type, String Meta);

    // 改变个人 Token 余额
    void changeUserTokenBalance(Long userId, Long operator, String traceId, Integer changedToken, WalletTransactionType type, String Meta);

    // 更新组成员 Token 配额
    void updateGroupMemberTokenLimit(GroupMemberTokenLimitUpdateRequest req);

    // 更新组 Token 用量
    void updateGroupTokenUsed(Long groupId, Integer usedToken);

    // 更新组成员 Token 用量
    Integer updateGroupMemberTokenUsed(Long groupId, Long userId, String traceId, Integer tokenBill, String BillMeta, String billingDetail);

    // 更新个人 Token 用量
    void updateUserTokenUsed(Long userId, String traceId, Integer tokenBill, String billMeta, String billingDetail);

    // 转移 Token（仅限小组所有者）
    void transferTokenBetweenGroupAndUser(Long userId, WalletTransferTokenRequest req);

    void transferTokenBetweenGroupAndUser(Long userId, Long groupId, Integer tokenCount, TokenTransferType tokenTransferType);

    // 核销点卡
    void redeemVoucher(Long userId, String voucherCode);

    //获取交易流水
    PageR<WalletTransactionRecordResponse> listTransactions(
            WalletPayerType payerType,
            Long payerId,
            List<WalletTransactionType> walletTransactionTypes,
            WalletBusinessType walletBusinessType,
            Integer page, Integer size
    );

    // 获取个人钱包详情
    WalletDetailResponse getUserWalletInfo(Long userId);

    // 批量获得用户所有小组的 Token 信息
    PageR<GroupMemberTokenDetailResponse> getAllGroupTokenInfoByUserId(Long userId, Integer page, Integer size);

    // 改变个人金币余额
    void changeCoinBalance(Long userId, Long operatorId, String traceId, Integer changedCoin, WalletTransactionType type, String meta);

    // 结算金币交易
    void settleCoinTrade(WalletSettleCoinTradeRequest req);
}
