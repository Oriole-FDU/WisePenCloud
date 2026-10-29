package com.oriole.wisepen.system.api.domain.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackDetailResponse extends FeedbackListItemResponse {
    @Schema(description = "反馈正文")
    private String content;

    @Schema(description = "联系方式", nullable = true)
    private String contact;

    @Schema(description = "提交时保存的反馈图片地址", nullable = true)
    private String imageUrl;

    @Schema(description = "用户浏览器信息", nullable = true)
    private String browser;
}
