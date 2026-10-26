package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import lombok.Data;

import java.io.Serializable;

/**
 * 注册入参中的档案部分，仅供注册流程写入档案时使用。
 * <p>
 * 与 {@link UserProfileBase} 字段基本一致，但故意不含 {@code inviteCode}，
 * 避免注册载荷覆盖系统生成的个人邀请码。
 */
@Data
public class UserRegistrationProfileBase implements Serializable {
    private GenderType sex;
    private String university;
    private String college;
    private String major;
    private String className;
    private Integer enrollmentYear;
    private DegreeLevel degreeLevel;
    private String academicTitle;
}
