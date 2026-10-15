package com.oriole.wisepen.campus.extension.api.domain.dto;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 扩展服务确认并归一化后的校园身份资料
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifiedIdentityClaimsDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 学校认证系统确认的真实姓名 */
    private String realName;

    /** 学校认证系统确认的学号或职工号，用户服务会按该字段做唯一性校验 */
    private String campusNo;

    /** 标准用户身份类型，扩展服务负责把学校原始身份映射为 STUDENT/TEACHER */
    private IdentityType identityType;

    /** 学校展示名称 */
    private String universityName;

    /** 院系或部门名称 */
    private String college;

    /** 学生专业（用户身份为教师时为空） */
    private String major;

    /** 学生班级（用户身份为教师时为空）*/
    private String className;

    /** 入学年份（用户身份为教师时为空） */
    private Integer enrollmentYear;

    /** 学历层次（用户身份为教师时为空） */
    private DegreeLevel degreeLevel;

    /** 标准性别枚举 */
    private GenderType gender;

    /** 教师职称（用户身份为学生时为空） */
    private String academicTitle;

    /** 学校认证系统提供的邮箱，可为空 */
    private String email;

    /** 学校认证系统提供的手机号，可为空 */
    private String mobile;
}
