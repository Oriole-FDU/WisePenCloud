package com.oriole.wisepen.questionnaire.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问卷微服务专属业务错误。
 */
@Getter
@AllArgsConstructor
public enum QuestionnaireError implements IResult {
    TABLE_NOT_FOUND(10111, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE, ErrorReason.NOT_FOUND), "表格不存在"),
    TABLE_ALREADY_EXISTS(10121, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE, ErrorReason.ALREADY_EXISTS), "表格已存在"),

    TABLE_VERSION_NOT_FOUND(10211, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_VERSION, ErrorReason.NOT_FOUND), "表格版本不存在"),
    TABLE_VERSION_STATUS_INVALID(10221, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_VERSION, ErrorReason.STATE_INVALID), "表格版本状态非法"),
    TABLE_VERSION_CONFLICT(10231, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_VERSION, ErrorReason.CONFLICT), "请求版本与当前草稿版本不一致"),

    SUBMISSION_POLICY_INVALID(10321, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION_POLICY, ErrorReason.INVALID), "问卷提交策略非法"),

    TABLE_COLUMN_DUPLICATED(10411, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_COLUMN, ErrorReason.ALREADY_EXISTS), "表格字段重复"),
    TABLE_COLUMN_INVALID(10421, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_COLUMN, ErrorReason.INVALID), "表格字段定义非法"),

    QUESTIONNAIRE_PERMISSION_DENIED(10521, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.PERMISSION_DENIED), "问卷权限不足"),
    QUESTIONNAIRE_REGISTER_RESOURCE_FAILED(10531, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.EXTERNAL_FAILED), "问卷资源注册失败"),
    QUESTIONNAIRE_GET_RESOURCE_FAILED(10532, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.EXTERNAL_FAILED), "获取问卷资源失败"),
    QUESTIONNAIRE_INITIALIZATION_FAILED(10541, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.FAILED), "问卷初始化失败"),
    QUESTIONNAIRE_PUBLISH_FAILED(10542, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.FAILED), "问卷发布失败"),
    QUESTIONNAIRE_FORK_FAILED(10543, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.FAILED), "问卷复制失败"),

    SUBMISSION_NOT_FOUND(10611, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_FOUND), "答卷不存在"),
    SUBMISSION_COLUMN_NOT_FOUND(10612, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_FOUND), "答卷题目不存在"),
    SUBMISSION_NOT_STARTED(10621, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_ALLOWED), "问卷填写尚未开始"),
    SUBMISSION_DRAFT_NOT_ALLOWED(10622, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_ALLOWED), "当前不允许保存答卷草稿"),
    SUBMISSION_ENDED(10623, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_ALLOWED), "问卷填写已结束"),
    SUBMISSION_UPDATE_NOT_ALLOWED(10624, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_ALLOWED), "当前不允许修改已提交答卷"),
    SUBMISSION_LIMIT_REACHED(10625, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_ALLOWED), "已达到问卷提交次数上限"),
    SUBMISSION_VALUE_INVALID(10631, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.INVALID), "答卷内容非法"),
    SUBMISSION_QUERY_INVALID(10632, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.INVALID), "答卷查询条件非法");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
