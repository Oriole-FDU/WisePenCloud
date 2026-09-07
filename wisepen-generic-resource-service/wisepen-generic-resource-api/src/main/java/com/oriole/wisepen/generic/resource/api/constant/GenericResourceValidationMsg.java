package com.oriole.wisepen.generic.resource.api.constant;

/**
 * 通用资源服务字段校验提示常量
 */
public class GenericResourceValidationMsg {

    public static final String FILENAME_EMPTY = "文件名不能为空";
    public static final String MD5_EMPTY = "MD5不能为空";
    public static final String FILE_SIZE_NULL = "文件大小不能为空";
    public static final String FILE_SIZE_POSITIVE = "文件大小必须大于0";
    public static final String GENERIC_RESOURCE_ID_EMPTY = "通用资源记录ID不能为空";
    public static final String RESOURCE_ID_EMPTY = "资源ID不能为空";

    private GenericResourceValidationMsg() {
    }
}
