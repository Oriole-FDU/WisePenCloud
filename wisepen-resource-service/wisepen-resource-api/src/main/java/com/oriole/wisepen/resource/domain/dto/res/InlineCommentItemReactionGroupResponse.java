package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InlineCommentItemReactionGroupResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "表情 ID")
    private String emojiId;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "该表情的回应数")
    private Integer count = 0;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前用户是否回应过该表情")
    private Boolean reactedByCurrentUser = false;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "该表情的回应用户列表")
    private List<UserDisplayBase> users = new ArrayList<>();
}
