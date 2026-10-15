package com.oriole.wisepen.user.strategy;

import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;

import java.util.List;
import java.util.Map;

/**
 * 用户身份验证策略接口
 */
public interface UserVerificationStrategy {

    /**
     * 获取当前策略的唯一标识
     */
    UserVerificationMode getMode();

    /**
     * 数据主权：获取该策略下被强接管、禁止用户手动修改的字段列表
     * 用于后端更新时的自动过滤，以及前端的 UI 只读控制
     */
    List<String> getReadonlyFields();
}