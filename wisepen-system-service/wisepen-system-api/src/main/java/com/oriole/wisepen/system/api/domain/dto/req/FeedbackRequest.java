package com.oriole.wisepen.system.api.domain.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author Xiong Heng
 */
@Data
public class FeedbackRequest {
    @NotBlank(message = "反馈内容不能为空")
    private String content;

    @NotBlank(message = "联系方式不能为空")
    private String contact;
    private String imageUrl;

    private boolean bugReport;
    private boolean suggestion;
    private boolean consultation;
    private boolean complaint;
    private boolean other;

    @JsonIgnore
    @AssertTrue(message = "至少选择一种反馈类型")
    public boolean isFeedbackTypeSelected() {
        return bugReport || suggestion || consultation || complaint || other;
    }
}
