package com.oriole.wisepen.user.api.config;

import com.oriole.wisepen.user.api.enums.RewardType;
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

    @Data
    public static class Rule {
        private Boolean enabled = false;
        private RewardType rewardType = RewardType.NONE;
        private Integer rewardAmount = 0;
    }

    @Data
    public static class DailyCheckIn {
        private Boolean enabled = false;
        private RewardType rewardType = RewardType.TOKEN;
        private Integer minRewardAmount = 100000;
        private Integer maxRewardAmount = 1000000;
        private Integer rewardStepAmount = 100000;
        private Integer refreshCycleDays = 1;
        private Integer guaranteeCycleCount = 10;
    }
}
