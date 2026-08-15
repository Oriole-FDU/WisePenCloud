package com.oriole.wisepen.questionnaire.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 表格/问卷视图微服务专属业务错误。
 */
@Getter
@AllArgsConstructor
public enum TableError implements IResult {
    TABLE_NOT_FOUND(10111, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE, ErrorReason.NOT_FOUND), "表格不存在"),
    TABLE_ALREADY_EXISTS(10112, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE, ErrorReason.ALREADY_EXISTS), "表格已存在"),
    TABLE_REGISTER_RESOURCE_FAILED(10113, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE, ErrorReason.EXTERNAL_FAILED), "问卷资源注册失败"),
    TABLE_VERSION_NOT_FOUND(10211, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_VERSION, ErrorReason.NOT_FOUND), "表格版本不存在"),
    TABLE_VERSION_STATUS_INVALID(10213, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_VERSION, ErrorReason.STATE_INVALID), "表格版本状态非法"),
    QUESTIONNAIRE_VIEW_NOT_FOUND(10311, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE_VIEW, ErrorReason.NOT_FOUND), "问卷视图不存在"),
    QUESTIONNAIRE_VIEW_INVALID(10313, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE_VIEW, ErrorReason.INVALID), "问卷视图定义非法"),
    TABLE_COLUMN_DUPLICATED(10412, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_COLUMN, ErrorReason.ALREADY_EXISTS), "表格字段重复"),
    TABLE_COLUMN_INVALID(10413, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE_COLUMN, ErrorReason.INVALID), "表格字段定义非法"),
    QUESTIONNAIRE_VIEW_COLUMN_NOT_FOUND(10312, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE_VIEW, ErrorReason.NOT_FOUND), "问卷视图引用的字段不存在"),
    QUESTIONNAIRE_PERMISSION_DENIED(10513, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.TABLE, ErrorReason.PERMISSION_DENIED), "问卷权限不足"),
    SUBMISSION_NOT_FOUND(10611, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_FOUND), "答卷不存在"),
    SUBMISSION_NOT_ALLOWED(10613, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.NOT_ALLOWED), "当前不允许提交答卷"),
    SUBMISSION_VALUE_INVALID(10614, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.SUBMISSION, ErrorReason.INVALID), "答卷内容非法");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
