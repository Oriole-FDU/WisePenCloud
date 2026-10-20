package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.CommentType;
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
public class ResourceCommentBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "被回复用户 ID")
    private String replyToUserId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论作者 ID")
    private String authorId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论内容")
    private String content;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论图片 URL 列表")
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "点赞数")
    private Integer likeCount = 0;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "回复数")
    private Integer replyCount = 0;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论类型")
    private CommentType commentType;
}
