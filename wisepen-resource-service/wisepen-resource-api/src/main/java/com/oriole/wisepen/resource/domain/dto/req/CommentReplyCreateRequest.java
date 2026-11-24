package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommentReplyCreateRequest {
    @NotBlank
    private String resourceId;
    /** 被回复目标评论 ID */
    @NotBlank
    private String replyTo;
    @NotBlank
    private String content;
    private List<String> imageUrls = new ArrayList<>();

    /** true 表示该回复由 AI 代笔生成 */
    private Boolean aiAuthored;
}
