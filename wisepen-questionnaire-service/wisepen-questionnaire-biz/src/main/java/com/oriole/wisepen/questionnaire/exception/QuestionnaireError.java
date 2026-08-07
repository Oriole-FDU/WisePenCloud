package com.oriole.wisepen.questionnaire.exception;

import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.common.core.domain.ResultKey;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.exception.ErrorReason;
import com.oriole.wisepen.questionnaire.api.constant.QuestionnaireSubject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问卷微服务专属业务错误
 */
@Getter
@AllArgsConstructor
public enum QuestionnaireError implements IResult {
    QUESTIONNAIRE_NOT_FOUND(10111, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.NOT_FOUND), "问卷不存在"),
    QUESTIONNAIRE_ALREADY_EXISTS(10112, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE, ErrorReason.ALREADY_EXISTS), "问卷已存在"),
    QUESTIONNAIRE_VERSION_NOT_FOUND(10211, new ResultKey(BusinessDomain.QUESTIONNAIRE, QuestionnaireSubject.QUESTIONNAIRE_VERSION, ErrorReason.NOT_FOUND), "问卷版本不存在");

    private final Integer code;
    private final ResultKey key;
    private final String msg;
}
