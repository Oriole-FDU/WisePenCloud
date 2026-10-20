package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserAdminListItemResponse implements Serializable {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "用户 ID")
    private Long userId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户名")
    private String username;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "昵称")
    private String nickname;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "身份类型")
    private IdentityType identityType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "账号状态")
    private UserStatus userStatus;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "头像")
    private String avatar;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "真实姓名")
    private String realName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "学工号")
    private String campusNo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "邮箱")
    private String email;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "手机号")
    private String mobile;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "认证方式")
    private UserVerificationMode verificationMode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "认证 Provider 编码")
    private String verificationProviderCode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "更新时间")
    private LocalDateTime updateTime;
}
