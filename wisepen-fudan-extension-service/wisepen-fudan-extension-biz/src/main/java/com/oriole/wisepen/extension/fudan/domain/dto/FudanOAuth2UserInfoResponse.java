package com.oriole.wisepen.extension.fudan.domain.dto;

import lombok.Data;

/**
 * 复旦统一身份认证用户信息接口的响应
 */
@Data
public class FudanOAuth2UserInfoResponse {

    /** 学号/教职工号 */
    private String user_id;

    /** 姓名 */
    private String user_name;

    /** 用户类型 */
    private String user_type;

    /** 手机号 */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 隶属单位/部门 */
    private String department;

    /** IAM 登录会话 ID */
    private String sessionId;

    /** 失败时的错误码 */
    private String errcode;

    /** 失败时的错误说明 */
    private String msg;
}
