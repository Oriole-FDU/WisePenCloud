package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserSearchUserResponse extends UserDisplayBase {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "用户 ID")
    private Long userId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户名")
    private String username;
}
