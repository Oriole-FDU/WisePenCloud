package com.oriole.wisepen.system.service.executor.user.register;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UserRegisterTaskMeta {

    private List<UserMeta> users;

    @Data
    @NoArgsConstructor
    public static class UserMeta {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
        private IdentityType identityType;
        private String realName;
        private String campusNo;
        private String email;
        private String mobile;
        private String verificationMode;
        private String verificationProviderCode;
        private UserStatus userStatus;
        private ProfileMeta profile;
    }

    @Data
    @NoArgsConstructor
    public static class ProfileMeta {
        private String sex;
        private String university;
        private String college;
        private String major;
        private String className;
        private Integer enrollmentYear;
        private String degreeLevel;
        private String academicTitle;
    }
}
