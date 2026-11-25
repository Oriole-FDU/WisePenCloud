package com.oriole.wisepen.system.api.domain.dto.req;

import com.oriole.wisepen.system.api.enums.FeedbackType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class FeedbackRequest {
    private String content;
    private String contact;
    @Size(max = 1024, message = "图片地址长度不能超过1024字符")
    private String imageUrl;
    private String browser;
    @NotEmpty(message = "至少选择一种反馈类型")
    private Set<@NotNull FeedbackType> type;
}
