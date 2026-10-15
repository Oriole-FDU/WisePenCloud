package com.oriole.wisepen.campus.extension.api.enums;

/** 跨服务统一的校园身份认证任务状态 */
public enum CampusIdentityTaskState {
    /** 扩展服务任务排队中 */
    QUEUED,

    /** 扩展服务已受理任务，正在登录或等待外部系统响应 */
    PENDING,

    /** 需要用户继续完成登录挑战，例如扫码或输入验证码 */
    CHALLENGE_REQUIRED,

    /** 扩展服务已确认身份并返回标准身份资料 */
    SUCCESS,

    /** 认证失败，通常由账号密码错误、外部系统异常等原因导致 */
    FAILED,

    /** 认证任务或登录挑战已过期 */
    EXPIRED
}
