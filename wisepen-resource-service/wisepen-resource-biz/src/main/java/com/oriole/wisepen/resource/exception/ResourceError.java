package com.oriole.wisepen.resource.exception;
import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.resource.constant.ResourceSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源微服务(5)专属业务错误
 */
@Getter
@AllArgsConstructor
public enum ResourceError implements IResult {

    // Tag节点相关异常
    TAG_NODE_NOT_FOUND(5111, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_NODE, ErrorReason.NOT_FOUND), "标签节点不存在"),
    PARENT_TAG_NODE_NOT_FOUND(5112, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_NODE, ErrorReason.NOT_FOUND), "父标签节点不存在"),
    TAG_NODE_NAME_CONFLICT(5121, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_NODE, ErrorReason.CONFLICT),"同级目录下已存在同名标签节点"),
    CANNOT_SET_TAG_NODE_VISIBILITY(5131, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_NODE, ErrorReason.NOT_ALLOWED), "不能设置个人标签节点的可见范围"),

    // Tag路径节点相关异常
    CANNOT_USE_RESERVED_TAG_PATH_NODE_NAME(5211, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED),"不能使用系统保留名称(/ 或 .Trash)作为路径节点名称"),
    CANNOT_MODIFY_SYSTEM_TAG_PATH_NODE(5212, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED), "不能修改系统路径节点"),
    CANNOT_MOVE_SYSTEM_TAG_PATH_NODE(5213, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED), "不能移动系统路径节点"),
    CANNOT_DELETE_SYSTEM_TAG_PATH_NODE(5214, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED),"不能删除系统路径节点"),
    CANNOT_DELETE_TAG_PATH_NODE_DIRECTLY(5215, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED),"不能直接删除路径节点，请先移入回收站"),
    CANNOT_OPERATE_TRASHED_TAG_PATH_NODE(5216, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED),"不能操作回收站内的路径节点"),

    // Tag树相关异常
    CANNOT_MOVE_TAG_NODE_ACROSS_GROUP(5311, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_TREE, ErrorReason.NOT_ALLOWED), "不能跨小组移动标签节点"),
    CANNOT_MOVE_TAG_NODE_ACROSS_TAG_TYPE(5312, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_TREE, ErrorReason.NOT_ALLOWED),"不能跨节点类型(目录/标签)移动或挂载标签节点"),
    CANNOT_MOVE_TAG_NODE_TO_SELF(5321, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_TREE, ErrorReason.UNSUPPORTED), "不能将标签节点移动到自身之下"),
    CANNOT_MOVE_TAG_NODE_TO_DESCENDANT(5322, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_TREE, ErrorReason.UNSUPPORTED), "不能将标签节点移动到其子孙节点之下"),

    // 资源相关异常
    RESOURCE_NOT_FOUND(5411, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.NOT_FOUND),"资源不存在"),
    RESOURCE_PERMISSION_DENIED(5421, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.PERMISSION_DENIED),"无权访问或操作该资源"),
    SCORE_OUT_OF_RANGE(5431, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.INVALID), "评分必须在1到5之间"),
    RESOURCE_SEARCH_FAILED(5441, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.FAILED), "搜索资源失败"),

    // 资源标签相关异常
    CANNOT_BIND_RESOURCE_TO_MULTIPLE_PATH_NODES(5511, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.NOT_ALLOWED),"不能为资源绑定多个路径节点"),
    CANNOT_PLACE_RESOURCE_PATH_TAG_AFTER_TAGS(5512, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.NOT_ALLOWED),"不能将资源路径标签放在普通标签之后"),
    CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE(5521, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.ALREADY_EXISTS),"该资源已绑定标签，文件夹模式下不能重复绑定"),
    BIND_RESOURCE_TO_TAG_NODE_DENIED(5531, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.PERMISSION_DENIED),"无权挂载资源到该标签下"),

    // 小组资源管理模式异常
    CANNOT_CHANGE_FILE_ORG_LOGIC_FROM_TAG_TO_FOLDER(5611, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.FILE_ORG_LOGIC, ErrorReason.UNSUPPORTED), "小组资源管理模式不允许从TAG改为FOLDER"),

    // 收藏相关异常
    FAVORITE_COLLECTION_NOT_FOUND(5711, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.FAVORITE, ErrorReason.NOT_FOUND), "收藏集合不存在"),
    DEFAULT_COLLECTION_CANNOT_DELETE(5721, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.FAVORITE, ErrorReason.NOT_ALLOWED), "默认收藏集合不可删除");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}