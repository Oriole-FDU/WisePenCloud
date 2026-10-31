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
    @Schema(
            description = "用户 ID",
            type = "string",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Long userId;

    @Schema(description = "用户名", nullable = true)
    private String username;

    @Schema(description = "昵称", nullable = true)
    private String nickname;

    @Schema(
            description = "身份类型：1=学生，2=教师，3=管理员",
            type = "integer",
            allowableValues = {"1", "2", "3"},
            nullable = true
    )
    private IdentityType identityType;

    @Schema(
            description = "账号状态",
            type = "string",
            allowableValues = {"NORMAL", "UNIDENTIFIED", "BANNED"},
            nullable = true
    )
    private UserStatus userStatus;
    @Schema(description = "头像", nullable = true)
    private String avatar;

    @Schema(description = "真实姓名", nullable = true)
    private String realName;

    @Schema(description = "学工号", nullable = true)
    private String campusNo;

    @Schema(description = "邮箱", nullable = true)
    private String email;

    @Schema(description = "手机号码", nullable = true)
    private String mobile;
    @Schema(description = "当前认证方式", nullable = true)
    private UserVerificationMode verificationMode;

    @Schema(description = "当前认证 Provider 编码", nullable = true)
    private String verificationProviderCode;

    @Schema(
            description = "账号创建时间，Unix 毫秒时间戳",
            type = "integer",
            format = "int64",
            nullable = true
    )
    private LocalDateTime createTime;

    @Schema(
            description = "账号更新时间，Unix 毫秒时间戳",
            type = "integer",
            format = "int64",
            nullable = true
    )
    private LocalDateTime updateTime;
}
