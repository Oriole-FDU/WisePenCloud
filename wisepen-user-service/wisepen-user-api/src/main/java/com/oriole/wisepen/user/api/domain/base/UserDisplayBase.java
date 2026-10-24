package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserDisplayBase implements Serializable {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "昵称")
    private String nickname;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户名")
    private String username;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "头像")
    private String avatar;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "身份类型")
    private IdentityType identityType; // 身份

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "真实姓名")
    private String realName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "学工号")
    private String campusNo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "邮箱")
    private String email;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "手机号")
    private String mobile;

}
