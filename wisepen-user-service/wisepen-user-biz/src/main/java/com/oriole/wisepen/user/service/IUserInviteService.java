package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.user.api.domain.dto.res.UserInviteRecordResponse;

public interface IUserInviteService {

    // 生成用户专属邀请码
    String generateInviteCode();

    // 注册时绑定邀请关系
    void bindAtRegistration(Long inviteeUserId, String inviteCode);

    // 用户认证成功后兑现邀请奖励
    void rewardInviterAfterVerification(Long inviteeUserId);

    // 分页查询用户发出的邀请记录
    PageR<UserInviteRecordResponse> listInviteRecords(Long inviterUserId, Integer page, Integer size);
}
