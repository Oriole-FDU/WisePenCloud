package com.oriole.wisepen.media.api.constant;

/**
 * 媒体服务字段校验提示常量。
 */
public final class MediaValidationMsg {

    public static final String FILENAME_EMPTY = "文件名不能为空";
    public static final String EXTENSION_EMPTY = "文件扩展名不能为空";
    public static final String MD5_EMPTY = "MD5不能为空";
    public static final String FILE_SIZE_NULL = "文件大小不能为空";
    public static final String FILE_SIZE_POSITIVE = "文件大小必须大于0";
    public static final String MEDIA_ID_EMPTY = "媒体ID不能为空";
    public static final String RESOURCE_ID_EMPTY = "资源ID不能为空";
    public static final String SESSION_ID_EMPTY = "会话ID不能为空";

    private MediaValidationMsg() {
    }
}
