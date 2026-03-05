package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户详细档案实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_profile")
public class UserProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String realName;

    @TableField("sex")
    private GenderType sex;

    private String university;
    private String college;

    private String major;

    @TableField("class_name")
    private String className;

    @TableField("enrollment_year")
    private Integer enrollmentYear;

    @TableField("degree_level")
    private DegreeLevel degreeLevel;

    @TableField("academic_title")
    private String academicTitle;
}