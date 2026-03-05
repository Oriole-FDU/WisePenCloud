package com.oriole.wisepen.common.core.constant;

public class SecurityConstants {
    /** Cookie的Authorization Token */
    public static final String COOKIE_AUTHORIZATION_TOKEN = "authorization";

    /** 网关透传的用户ID Header Key */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关透传的用户身份 Header Key */
    public static final String HEADER_IDENTITY_TYPE = "X-Identity-Type";

    /** 网关透传的组ID Header Key */
    public static final String HEADER_GROUP_IDS = "X-Group-Ids";

    /** 内部服务调用时的鉴权 Header (防止绕过网关直连) */
    public static final String HEADER_FROM_SOURCE = "X-From-Source";
}