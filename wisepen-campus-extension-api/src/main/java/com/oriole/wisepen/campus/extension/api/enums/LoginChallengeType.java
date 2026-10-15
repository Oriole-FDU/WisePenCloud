package com.oriole.wisepen.campus.extension.api.enums;

/** Provider 要求用户完成的登录 Challenge 类型 */
public enum LoginChallengeType {
    /** 二维码扫码确认 */
    QR_SCAN,

    /** 图形或文本验证码 */
    CAPTCHA,
}
