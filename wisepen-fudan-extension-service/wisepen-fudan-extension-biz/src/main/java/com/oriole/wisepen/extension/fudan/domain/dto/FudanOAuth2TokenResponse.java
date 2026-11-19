package com.oriole.wisepen.extension.fudan.domain.dto;

import lombok.Data;

/**
 * 复旦统一身份认证换取令牌接口的响应
 */
@Data
public class FudanOAuth2TokenResponse {

    private String access_token;

    private String refresh_token;

    private Integer expires_in;

    private String token_type;

    /** 失败时的错误码 */
    private String errcode;

    /** 失败时的错误说明 */
    private String msg;
}
