package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InlineCommentItemCreateRequest {
    @NotBlank
    private String resourceId;

    @NotBlank
    private String inlineCommentId;

    private Integer contentVersion;

    @NotBlank
    private String content;

    private List<String> imageUrls = new ArrayList<>();

    private List<String> mentionUserIds = new ArrayList<>();

    /** true 表示该消息由 AI 代笔生成 */
    private Boolean aiAuthored;
}
