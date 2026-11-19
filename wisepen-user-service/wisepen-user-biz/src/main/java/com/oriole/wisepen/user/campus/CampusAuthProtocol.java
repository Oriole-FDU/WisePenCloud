package com.oriole.wisepen.user.campus;

/**
 * 校园认证协议
 */
public enum CampusAuthProtocol {

    /** OAuth2.0 认证 */
    OAUTH2,

    /** 传统认证（使用账号密码通过模拟请求的方式认证） */
    CLASSIC
}
