package com.oriole.wisepen.user.api.domain.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupDetailInfoResponse extends GroupItemInfoResponse {
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "加入小组的邀请码")
	private String inviteCode;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已使用 Token")
	private Integer tokenUsed;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "剩余 Token")
	private Integer tokenBalance;
}
