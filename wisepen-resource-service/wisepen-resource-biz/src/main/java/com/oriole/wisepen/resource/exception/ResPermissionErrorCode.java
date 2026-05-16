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

    CROSS_TYPE_OPERATION_NOT_ALLOWED(50007, "禁止跨节点类型(目录/标签)进行移动或挂载"),
    CANNOT_MODIFY_SYSTEM_NODE(50008, "系统级保留节点禁止修改名称或属性"),
    CANNOT_MOVE_SYSTEM_NODE(50009, "系统级保留节点禁止移动"),
    TAG_NAME_DUPLICATE(50010, "同级目录下已存在同名标签或文件夹"),
    CANNOT_DELETE_SYSTEM_NODE(50011, "系统级保留节点禁止删除"),
    CANNOT_DELETE_PATH_DIRECTLY(50012, "文件夹节点禁止直接物理删除，请使用移入回收站操作"),
    CANNOT_USE_SYSTEM_RESERVED_NAME(50013, "禁止使用系统保留字(/ 或 .Trash)作为节点名称"),
    CANNOT_OPERATE_IN_TRASH(50014, "回收站内的节点已被冻结，禁止进行创建、修改或内部移动"),
    NODE_NOT_IN_TRASH(50015, "只能彻底删除位于回收站内的节点"),

    // --- 资源相关异常 ---
    RESOURCE_NOT_FOUND(50101, "目标标签不存在"),
    RESOURCE_PERMISSION_DENIED(50102, "对不起，您没有该资源的访问/操作权限"),
    PERSONAL_SPACE_MUST_HAVE_ONE_PATH(50103, "个人空间的资源必须且只能存在一个路径目录"),
    PATH_MUST_BE_FIRST_TAG(50104, "资源的路径目录必须位于标签列表的首位"),

    // --- 小组资源配置相关异常 ---
    FOLDER_MODE_ONLY_ONE_TAG(50201, "文件夹模式下每个资源在同一小组内至多只能挂载一个标签"),
    TAG_MOUNT_DENIED(50202, "对不起，您没有该标签的挂载权限");

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