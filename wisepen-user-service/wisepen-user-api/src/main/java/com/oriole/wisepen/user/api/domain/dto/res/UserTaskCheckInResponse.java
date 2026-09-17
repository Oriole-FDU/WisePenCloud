package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.RewardType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserTaskCheckInResponse {
    private RewardType rewardType;
    private Integer rewardAmount;
    private Boolean hitGuarantee;
    private Integer cycleProgress;
    private Integer cycleCount;
    private Boolean checkedInToday;
}
