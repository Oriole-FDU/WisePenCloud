package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUserInteractionRecordBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否阅读过")
    private Boolean read = false;   // 用户是否阅读过该资源

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否点赞过")
    private Boolean liked = false;  // 用户是否赞过该资源

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "用户评分")
    private Integer score;  // 用户对该资源的评分

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已点赞的评论 ID 列表")
    private List<String> likedCommentIds = new ArrayList<>();  // 用户已点赞的评论/回复统一 commentId 列表
}
