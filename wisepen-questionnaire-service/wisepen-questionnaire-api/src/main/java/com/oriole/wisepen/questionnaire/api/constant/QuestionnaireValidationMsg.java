package com.oriole.wisepen.questionnaire.api.constant;

public interface QuestionnaireValidationMsg {
    String RESOURCE_ID_NOT_BLANK = "资源ID不能为空";
    String TABLE_DEFINITION_NOT_NULL = "表格定义不能为空";
    String QUESTIONNAIRE_VIEW_NOT_NULL = "问卷视图不能为空";
    String TITLE_NOT_BLANK = "标题不能为空";
    String COLUMNS_NOT_EMPTY = "表格列不能为空";
    String PAGES_NOT_EMPTY = "问卷页面不能为空";
    String PAGE_ID_NOT_BLANK = "页面ID不能为空";
    String COLUMN_ITEMS_NOT_EMPTY = "页面字段不能为空";
    String COLUMN_ID_NOT_BLANK = "字段ID不能为空";
    String COLUMN_NAME_NOT_BLANK = "字段名称不能为空";
    String COLUMN_TYPE_NOT_NULL = "字段类型不能为空";
    String OPTION_ID_NOT_BLANK = "选项ID不能为空";
    String OPTION_LABEL_NOT_BLANK = "选项内容不能为空";
    String SUBMISSION_POLICY_NOT_NULL = "填写规则不能为空";
}
