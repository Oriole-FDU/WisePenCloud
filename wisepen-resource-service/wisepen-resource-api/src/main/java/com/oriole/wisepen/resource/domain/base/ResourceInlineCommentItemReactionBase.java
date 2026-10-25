package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentItemReactionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "表情 ID")
    private String emojiId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "回应时间")
    private LocalDateTime createTime;
}
