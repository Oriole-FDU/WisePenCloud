package com.oriole.wisepen.campus.extension.api.enums;

/**
 * 登录 Challenge 的完成方式。
 */
public enum ChallengeCompletionMode {
    /** 用户在当前设备之外完成 Challenge，客户端通过任务查询获取结果。 */
    OUT_OF_BAND,
    /** 用户填写答案并调用 Challenge 提交接口。 */
    SUBMIT
}
