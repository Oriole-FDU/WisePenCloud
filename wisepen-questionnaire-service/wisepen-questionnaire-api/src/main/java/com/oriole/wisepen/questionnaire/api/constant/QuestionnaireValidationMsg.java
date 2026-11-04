package com.oriole.wisepen.questionnaire.api.constant;

public interface QuestionnaireValidationMsg {
    String RESOURCE_ID_NOT_BLANK = "资源ID不能为空";
    String SUBMISSION_ID_NOT_BLANK = "答卷ID不能为空";
    String PAGE_MIN_INVALID = "页码不能小于1";
    String SIZE_MIN_INVALID = "每页条数不能小于1";
    String SIZE_MAX_INVALID = "每页条数不能超过100";
    String FILTER_LOGIC_NOT_NULL = "筛选条件组合方式不能为空";
    String FILTER_OPERATOR_NOT_NULL = "筛选操作符不能为空";
    String SORT_DIRECTION_NOT_NULL = "排序方向不能为空";
    String VERSION_NOT_NULL = "版本号不能为空";
    String TITLE_NOT_BLANK = "标题不能为空";
    String COLUMN_ID_NOT_BLANK = "字段ID不能为空";
    String COLUMNS_NOT_NULL = "题目列表不能为空";
    String SUBMISSION_POLICY_NOT_NULL = "提交策略不能为空";
    String PAGE_NUMBER_NOT_NULL = "分页页码不能为空";
    String SIZE_NOT_NULL = "每页条数不能为空";
}
