package com.oriole.wisepen.user.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.user.api.constant.UserSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户存储微服务(3)专属业务错误
 */
@Getter
@AllArgsConstructor
public enum UserError implements IResult {

    // 认证相关异常
    AUTH_USERNAME_OR_PASSWORD_INVALID(3111, new ResultKey(BusinessDomain.USER, UserSubject.AUTH, ErrorReason.INVALID),"用户名或密码错误"),
    AUTH_USER_LOCKED(3121, new ResultKey(BusinessDomain.USER, UserSubject.AUTH, ErrorReason.LOCKED), "账号已禁用"),

    // 用户相关异常
    USERNAME_ALREADY_EXISTS(3211, new ResultKey(BusinessDomain.USER, UserSubject.USER, ErrorReason.ALREADY_EXISTS), "用户名已存在"),
    CAMPUS_NO_ALREADY_EXISTS(3212, new ResultKey(BusinessDomain.USER, UserSubject.USER, ErrorReason.ALREADY_EXISTS), "学号已存在"),
    USER_PASSWORD_RESET_EXPIRED(3221,  new ResultKey(BusinessDomain.USER, UserSubject.USER, ErrorReason.EXPIRED),"重置密码链接已过期"),
    USER_PASSWORD_RESET_EMAIL_SEND_FAILED(3231, new ResultKey(BusinessDomain.USER, UserSubject.USER, ErrorReason.FAILED),"重置密码邮件发送失败"),

    // 用户验证相关异常
    CANNOT_OPERATE_BEFORE_AUTH_VERIFICATION(3311, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.STATE_INVALID),"未完成身份验证的账号，不能进行该操作"),
    VERIFICATION_EMAIL_INVALID(3312, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.INVALID),"教育邮箱格式无效"),
    VERIFICATION_EMAIL_STATE_INVALID(3313, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.STATE_INVALID),"当前账号不能进行教育邮箱认证"),
    VERIFICATION_EMAIL_ALREADY_EXISTS(3321, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.ALREADY_EXISTS),"身份验证邮箱已被其他账号绑定"),
    VERIFICATION_CAMPUS_NO_ALREADY_EXISTS(3322, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.ALREADY_EXISTS), "身份验证学号已被其他账号绑定"),
    VERIFICATION_EMAIL_TOKEN_EXPIRED(3331, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.EXPIRED), "身份验证链接已过期"),
    VERIFICATION_EMAIL_SEND_FAILED(3341, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.FAILED),"身份验证邮件发送失败"),
    VERIFICATION_FUDAN_UIS_REQUEST_FAILED(3342, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.FAILED),"复旦 UIS 认证请求发送失败"),
    VERIFICATION_FUDAN_UIS_FAILED(3343, new ResultKey(BusinessDomain.USER, UserSubject.AUTH_VERIFICATION, ErrorReason.FAILED),"复旦 UIS 认证失败"),

    // 小组相关异常
    GROUP_NOT_EXIST(3411, new ResultKey(BusinessDomain.USER, UserSubject.GROUP, ErrorReason.NOT_FOUND),"小组不存在"),

    // 小组成员相关异常
    GROUP_MEMBER_NOT_FOUND(3511, new ResultKey(BusinessDomain.USER, UserSubject.GROUP_MEMBER, ErrorReason.NOT_FOUND),"小组成员不存在"),
    GROUP_MEMBER_ALREADY_EXISTS(3521, new ResultKey(BusinessDomain.USER, UserSubject.GROUP_MEMBER, ErrorReason.ALREADY_EXISTS),"小组成员已存在"),
    CANNOT_QUIT_GROUP_AS_OWNER(3531, new ResultKey(BusinessDomain.USER, UserSubject.GROUP_MEMBER, ErrorReason.NOT_ALLOWED), "组长不能退出小组"),

    // 钱包相关异常
    CANNOT_CONFIGURE_GROUP_WALLET_QUOTA(3611, new ResultKey(BusinessDomain.USER, UserSubject.WALLET, ErrorReason.NOT_ALLOWED),"小组不能配置配额"),
    // TOKEN钱包相关异常
    WALLET_TOKEN_LIMIT_BELOW_USED(3711, new ResultKey(BusinessDomain.USER, UserSubject.WALLET_TOKEN, ErrorReason.BELOW_LOWER_BOUND),"配额不能低于已用量"),
    // TOKEN点卡相关异常
    WALLET_VOUCHER_NOT_FOUND(3811, new ResultKey(BusinessDomain.USER, UserSubject.VOUCHER, ErrorReason.NOT_FOUND),"TOKEN 点卡不存在"),
    WALLET_VOUCHER_INVALID(3821, new ResultKey(BusinessDomain.USER, UserSubject.VOUCHER, ErrorReason.INVALID),"TOKEN 点卡不可用"),
    WALLET_VOUCHER_EXPIRED(3831, new ResultKey(BusinessDomain.USER, UserSubject.VOUCHER, ErrorReason.EXPIRED),"TOKEN 点卡已过期"),
    // Coin相关异常
    WALLET_COIN_INSUFFICIENT(3911, new ResultKey(BusinessDomain.USER, UserSubject.WALLET_COIN, ErrorReason.BELOW_LOWER_BOUND), "金币余额不足"),
    WALLET_COIN_CHANGE_FAILED(3921, new ResultKey(BusinessDomain.USER, UserSubject.WALLET_COIN, ErrorReason.FAILED), "金币变动失败"),

    // 用户消息相关异常
    MESSAGE_NOT_FOUND(31061, new ResultKey(BusinessDomain.USER, UserSubject.MESSAGE, ErrorReason.NOT_FOUND), "消息不存在"),
    MESSAGE_RECEIVER_REQUIRED(31062, new ResultKey(BusinessDomain.USER, UserSubject.MESSAGE, ErrorReason.REQUIRED_MISSING), "接收用户不能为空"),
    MESSAGE_DELIVERY_SCOPE_INVALID(31063, new ResultKey(BusinessDomain.USER, UserSubject.MESSAGE, ErrorReason.INVALID), "消息投递范围无效");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
