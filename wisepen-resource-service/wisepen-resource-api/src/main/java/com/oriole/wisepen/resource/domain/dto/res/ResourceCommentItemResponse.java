package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceCommentBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceCommentItemResponse extends ResourceCommentBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "评论 ID")
    private String commentId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "评论作者展示信息")
    private UserDisplayBase authorInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "被回复用户展示信息")
    private UserDisplayBase replyToUserInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否已删除")
    private Boolean deleted;
}
