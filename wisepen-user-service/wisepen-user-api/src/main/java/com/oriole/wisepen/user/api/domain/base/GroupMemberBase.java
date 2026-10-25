package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
public class GroupMemberBase implements Serializable {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "小组成员角色")
    private GroupRoleType role; // 用户角色

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "加入时间")
    private LocalDateTime joinTime; // 加入时间

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Token 限额")
    private Integer tokenLimit;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已使用 Token")
    private Integer tokenUsed;
}
