package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.enums.TokenGrantCode;

public interface ITokenGrantService {

    boolean grantMaxTimes(Long userId, TokenGrantCode grantCode, Integer maxTimes, Long operatorId, String meta);

    boolean grantDailyMaxTimes(Long userId, TokenGrantCode grantCode, Integer maxTimesPerDay, Long operatorId, String meta);

    boolean grantUnchecked(Long userId, TokenGrantCode grantCode, Long operatorId, String meta);
}
