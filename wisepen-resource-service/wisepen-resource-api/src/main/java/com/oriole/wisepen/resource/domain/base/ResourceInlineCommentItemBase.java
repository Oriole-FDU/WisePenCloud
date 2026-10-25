package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentItemBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论条目 ID")
    private String itemId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论作者 ID")
    private String authorId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论内容")
    private String content;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论图片 URL 列表")
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "被提及用户 ID 列表")
    private List<String> mentionUserIds = new ArrayList<>();

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "按用户分组的表情回应")
    private Map<String, List<ResourceInlineCommentItemReactionBase>> reactions = new HashMap<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "更新时间")
    private LocalDateTime updateTime;
}
