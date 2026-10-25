package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentResponse extends ResourceInlineCommentBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "行内评论 ID")
    private String inlineCommentId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "创建者展示信息")
    private UserDisplayBase creatorInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "解决人展示信息")
    private UserDisplayBase resolvedByInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "更新时间")
    private LocalDateTime updateTime;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论条目列表")
    private List<InlineCommentItemResponse> items = new ArrayList<>();
}
