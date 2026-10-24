package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserAdminDetailResponse implements Serializable {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "用户 ID")
    private Long userId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "性别")
    private GenderType sex;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "邀请码")
    private String inviteCode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "学校")
    private String university;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "学院")
    private String college;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "专业")
    private String major;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "班级")
    private String className;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "入学年份")
    private Integer enrollmentYear;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "学历层次")
    private DegreeLevel degreeLevel;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "职称")
    private String academicTitle;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "更新时间")
    private LocalDateTime updateTime;
}
