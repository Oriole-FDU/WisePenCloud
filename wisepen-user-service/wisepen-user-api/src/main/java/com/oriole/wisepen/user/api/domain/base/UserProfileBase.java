package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class UserProfileBase {
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
