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
    CANNOT_USE_RESERVED_TAG_PATH_NODE_NAME(5211, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_PATH_NODE, ErrorReason.NOT_ALLOWED),"不能使用系统保留名称(/、.Trash 或 .Shared)作为路径节点名称"),
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
    CANNOT_OPERATE_ANCESTOR_DESCENDANT_TAG_NODES(5323, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.TAG_TREE, ErrorReason.UNSUPPORTED), "不能批量操作互为祖先和子孙的标签节点"),

    // 资源相关异常
    RESOURCE_NOT_FOUND(5411, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.NOT_FOUND),"资源不存在"),
    RESOURCE_PERMISSION_DENIED(5421, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.PERMISSION_DENIED),"无权访问或操作该资源"),
    SCORE_OUT_OF_RANGE(5431, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.INVALID), "评分必须在1到5之间"),
    RESOURCE_SEARCH_FAILED(5441, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE, ErrorReason.FAILED), "搜索资源失败"),

    // 资源标签相关异常
    CANNOT_BIND_RESOURCE_TO_MULTIPLE_PATH_NODES(5511, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.NOT_ALLOWED),"不能为资源绑定多个路径节点"),
    CANNOT_PLACE_RESOURCE_PATH_TAG_AFTER_TAGS(5512, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.NOT_ALLOWED),"不能将资源路径标签放在普通标签之后"),
    CANNOT_BIND_MARKET_GROUP_TAG_DIRECTLY(5513, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.NOT_ALLOWED), "不能为资源直接绑定集市组标签"),
    CANNOT_BIND_MULTIPLE_RESOURCE_TAGS_IN_FOLDER_MODE(5521, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.ALREADY_EXISTS),"该资源已绑定标签，文件夹模式下不能重复绑定"),
    BIND_RESOURCE_TO_TAG_NODE_DENIED(5531, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.RESOURCE_TAG, ErrorReason.PERMISSION_DENIED),"无权挂载资源到该标签下"),

    // 小组资源管理模式异常
    CANNOT_CHANGE_FILE_ORG_LOGIC_FROM_TAG_TO_FOLDER(5611, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.FILE_ORG_LOGIC, ErrorReason.UNSUPPORTED), "小组资源管理模式不允许从TAG改为FOLDER"),

    // Market 相关异常
    MARKET_SALE_INFO_NOT_FOUND(5711, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.NOT_FOUND), "集市销售信息不存在"),
    MARKET_GROUP_NOT_FOUND(5712, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.NOT_FOUND), "集市组不存在"),
    MARKET_SALE_TIER_GRANT_ALREADY_EXISTS(5721,new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.ALREADY_EXISTS), "已拥有该售卖档位的授权"),
    CANNOT_REPUBLISH_BANNED_MARKET_SALE(5722, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.NOT_ALLOWED), "不能在集市发布已被封禁的资源"),
    CANNOT_OPERATE_OFF_SHELF_MARKET_SALE(5723, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.NOT_ALLOWED), "不能操作未上架或已下架的集市销售信息"),
    CANNOT_PURCHASE_OFF_SHELF_MARKET_SALE(5724, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.NOT_ALLOWED), "不能购买未上架的商品"),
    CANNOT_PURCHASE_OWN_MARKET_SALE(5731, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.NOT_ALLOWED), "不能购买自己上架的资源"),
    MARKET_ACTIONS_INVALID(5732, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.INVALID), "集市售卖信息设置的权限无效，不能包括集市组禁止的权限"),
    MARKET_SALE_TIER_NOT_FOUND(5733, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.INVALID), "集市售卖档位不存在"),
    MARKET_AUDIT_MESSAGE_INVALID(5734, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.INVALID), "集市售卖信息审核信息无效"),
    MARKET_SALE_TIER_ACTIONS_DUPLICATED(5735, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.CONFLICT), "集市售卖档位权限不能重复"),
    MARKET_AUDIT_VERSION_CONFLICT(5736, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.MARKET, ErrorReason.CONFLICT), "集市售卖信息审核版本与当前提交版本不一致"),

    // 收藏相关异常
    FAVORITE_COLLECTION_NOT_FOUND(5811, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.FAVORITE, ErrorReason.NOT_FOUND), "收藏集合不存在"),
    DEFAULT_COLLECTION_CANNOT_DELETE(5821, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.FAVORITE, ErrorReason.NOT_ALLOWED), "不能删除默认收藏集合"),

    // 评论相关异常
    COMMENT_NOT_FOUND(5911, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.COMMENT, ErrorReason.NOT_FOUND), "评论不存在"),
    COMMENT_DELETE_ACCESS_DENIED(5921, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.COMMENT, ErrorReason.PERMISSION_DENIED), "无权删除该评论或回复"),
    COMMENT_RESOLVE_ACCESS_DENIED(5922, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.COMMENT, ErrorReason.PERMISSION_DENIED), "无权解决该评论"),
    COMMENT_UPDATE_ACCESS_DENIED(5923, new ResultKey(BusinessDomain.RESOURCE, ResourceSubject.COMMENT, ErrorReason.PERMISSION_DENIED), "无权修改该评论");
    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
