package com.oriole.wisepen.resource.exception;
import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;

/**
 * 资源权限微服务专属业务错误码
 * 建议分配专属号段，例如 50000 - 59999
 */
@AllArgsConstructor
public enum ResPermissionErrorCode implements IErrorCode {

    // --- Tag树相关异常 ---
    TAG_NOT_FOUND(50001, "目标标签不存在"),
    PARENT_TAG_NOT_FOUND(50002, "父节点标签不存在"),
    CROSS_GROUP_MOVE_DENIED(50003, "禁止跨组移动标签"),
    CANNOT_MOVE_TO_SELF(50004, "不能将标签移动到自身之下"),
    CANNOT_MOVE_TO_DESCENDANT(50005, "不能将标签移动到其子孙节点之下"),
    CANNOT_SET_VISIBILITY(50006, "个人标签不能设置标签权限"),

    // --- 资源相关异常 ---
    RESOURCE_NOT_FOUND(50011, "目标标签不存在"),
    RESOURCE_PERMISSION_DENIED(50012, "对不起，您没有该资源的访问权限");

    private final Integer code;
    private final String msg;

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}