package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InlineCommentItemResponse extends ResourceInlineCommentItemBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "评论作者展示信息")
    private UserDisplayBase authorInfo;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "按表情分组的回应列表")
    private List<InlineCommentItemReactionGroupResponse> reactionGroups = new ArrayList<>();
}
