package com.oriole.wisepen.user.api.domain.base;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class GroupInfoBase extends GroupDisplayBase{
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "创建者用户 ID")
    private Long ownerId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "邀请码")
    private String inviteCode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "成员数")
    private Integer memberCount;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已使用 Token")
    private Integer tokenUsed;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "剩余 Token")
    private Integer tokenBalance;
}
