package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.InputControlType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 校园身份认证流程中的结构化输入定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputDefinitionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 输入值在 inputs 或 answers 中使用的键 */
    private String name;

    /** 直接展示给用户的标签 */
    private String label;

    /** 前端建议使用的控件类型 */
    private InputControlType controlType;

    /** 是否必须提供该输入 */
    private boolean required;

    /** 是否包含密码等敏感信息 */
    private boolean sensitive;

    /** 字符串值的最小长度；不限制时为空 */
    private Integer minLength;

    /** 字符串值的最大长度；不限制时为空 */
    private Integer maxLength;

    /** 字符串值需要匹配的正则表达式；不限制时为空 */
    private String pattern;

    /** SELECT 控件的可选值；其他控件通常为空列表 */
    private List<InputOptionDTO> options;

    /**
     * 校园身份认证输入项的可选值
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class InputOptionDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 提交给 Provider 的字符串值 */
        private String value;

        /** 直接展示给用户的标签 */
        private String label;
    }
}
