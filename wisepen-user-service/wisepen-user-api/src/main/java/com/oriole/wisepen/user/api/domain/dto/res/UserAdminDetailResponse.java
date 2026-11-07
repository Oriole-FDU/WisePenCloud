package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserAdminDetailResponse extends UserAdminListItemResponse {
    private GenderType sex;
    private String inviteCode;
    private String university;
    private String college;
    private String major;
    private String className;
    private Integer enrollmentYear;
    private DegreeLevel degreeLevel;
    private String academicTitle;
}