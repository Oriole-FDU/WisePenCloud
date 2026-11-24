package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommentCreateRequest {
    @NotBlank
    private String resourceId;
    @NotBlank
    private String content;
    private List<String> imageUrls = new ArrayList<>();

    /** true 表示该评论由 AI 代笔生成 */
    private Boolean aiAuthored;
}
