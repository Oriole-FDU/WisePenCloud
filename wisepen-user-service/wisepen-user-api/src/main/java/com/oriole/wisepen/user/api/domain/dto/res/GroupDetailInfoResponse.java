package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.AiBillingPolicy;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupDetailInfoResponse extends GroupItemInfoResponse {
	private String inviteCode;
	private Integer tokenUsed;
	private Integer tokenBalance;
	private AiBillingPolicy aiBillingPolicy;
}