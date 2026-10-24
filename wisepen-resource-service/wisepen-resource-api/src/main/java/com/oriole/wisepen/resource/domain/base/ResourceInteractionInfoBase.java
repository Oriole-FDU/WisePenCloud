package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 资源互动信息基类，承载阅读量等可持续扩展的互动统计字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInteractionInfoBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "有效阅读量")
    private Integer readCount = 0;       // 资源有效阅读量，默认 0

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "点赞总数")
    private Integer likeCount = 0;       // 点赞总数，默认 0

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "评分人数")
    private Integer scoreCount = 0;      // 评分人数，默认 0

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "评分总和")
    private Integer scoreTotal = 0;      // 评分总和，默认 0

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "收藏人数")
    private Integer favoriteCount = 0;   // 收藏人数（用户唯一收藏数），默认 0

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "评论总数")
    private Integer commentCount = 0;    // 评论总数（顶级评论 + 所有回复），默认 0
}
