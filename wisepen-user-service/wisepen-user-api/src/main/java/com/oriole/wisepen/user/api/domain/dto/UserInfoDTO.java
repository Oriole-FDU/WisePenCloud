package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserInfoDTO implements Serializable {

    private Long id;
    private String username;
    /** 密码哈希 注意：这个字段只在 User -> Auth 服务内部传输时有值。返回给前端时，必须手动置为 null，防止泄露！ */
    private String password;
    private IdentityType identityType;
    private Integer status;
    private String nickname;
    private String avatar;
    private String email;
    private String mobile;
    private LocalDateTime createTime;
    private String realName;
    private String campusNo;
    private GenderType sex;
    private String university;
    private String college;
    private String major;
    private String className;
    private Integer enrollmentYear;
    private DegreeLevel degreeLevel;
    private String academicTitle;
}