package com.oriole.wisepen.user.api.config;

import com.oriole.wisepen.user.api.enums.RewardType;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户任务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.user-task")
public class UserTaskProperties {

    private Map<String, Rule> rules = new HashMap<>();

    private DailyCheckIn dailyCheckIn = new DailyCheckIn();

    private InviteVerifiedUser inviteVerifiedUser = new InviteVerifiedUser();

    /** Nacos 未创建用户任务节点时使用的产品默认规则。 */
    public UserTaskProperties() {
        Rule onboardingTutorial = new Rule();
        onboardingTutorial.setEnabled(true);
        rules.put(UserTaskCode.ONBOARDING_TUTORIAL.getValue(), onboardingTutorial);

        Rule studentVerification = new Rule();
        studentVerification.setEnabled(true);
        studentVerification.setRewardType(RewardType.TOKEN);
        studentVerification.setRewardAmount(5_000_000);
        rules.put(UserTaskCode.STUDENT_VERIFICATION.getValue(), studentVerification);

        Rule teacherVerification = new Rule();
        teacherVerification.setEnabled(true);
        teacherVerification.setRewardType(RewardType.TOKEN);
        teacherVerification.setRewardAmount(100_000_000);
        rules.put(UserTaskCode.TEACHER_VERIFICATION.getValue(), teacherVerification);
    }

    @Data
    public static class Rule {
        private Boolean enabled = false;
        private RewardType rewardType = RewardType.NONE;
        private Integer rewardAmount = 0;
    }

    @Data
    public static class DailyCheckIn {
        private Boolean enabled = true;
        private RewardType rewardType = RewardType.TOKEN;
        private Integer minRewardAmount = 100000;
        private Integer maxRewardAmount = 1000000;
        private Integer rewardStepAmount = 100000;
        private Integer refreshCycleDays = 1;
        private Integer guaranteeCycleCount = 10;
    }

    @Data
    public static class InviteVerifiedUser {
        private Boolean enabled = true;
        private RewardType rewardType = RewardType.TOKEN;
        private Integer minRewardAmount = 1000000;
        private Integer maxRewardAmount = 10000000;
        private Integer rewardStepAmount = 1000000;
    }
}
