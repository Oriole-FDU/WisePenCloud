package com.oriole.wisepen.system.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 问卷问题类型枚举
 * 
 * @author Architecture Team
 */
@Getter
@AllArgsConstructor
public enum QuestionType {
    TEXT(1, "TEXT", "单行文本"),
    TEXTAREA(2, "TEXTAREA", "多行文本"),
    SELECT(3, "SELECT", "下拉单选"),
    RADIO(4, "RADIO", "单选框"),
    CHECKBOX(5, "CHECKBOX", "多选框"),
    FILE(6, "FILE", "文件上传");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
    
    private final String label;

    public static boolean isValid(String value) {
        for (QuestionType type : QuestionType.values()) {
            if (type.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
