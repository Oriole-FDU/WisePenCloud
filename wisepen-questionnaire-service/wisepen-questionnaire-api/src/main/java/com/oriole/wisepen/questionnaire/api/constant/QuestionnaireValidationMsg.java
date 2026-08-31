package com.oriole.wisepen.questionnaire.api.constant;

public interface QuestionnaireValidationMsg {
    String RESOURCE_ID_NOT_BLANK = "资源ID不能为空";
    String DEFINITION_NOT_NULL = "问卷定义不能为空";
    String TITLE_NOT_BLANK = "问卷标题不能为空";
    String PAGES_NOT_NULL = "问卷页面不能为空";
    String PAGE_ID_NOT_BLANK = "页面ID不能为空";
    String QUESTIONS_NOT_NULL = "页面题目不能为空";
    String QUESTION_ID_NOT_BLANK = "题目ID不能为空";
    String QUESTION_TYPE_NOT_NULL = "题目类型不能为空";
    String QUESTION_TITLE_NOT_BLANK = "题目标题不能为空";
    String OPTION_ID_NOT_BLANK = "选项ID不能为空";
    String OPTION_LABEL_NOT_BLANK = "选项内容不能为空";
    String SUBMISSION_POLICY_NOT_NULL = "填写规则不能为空";
}
