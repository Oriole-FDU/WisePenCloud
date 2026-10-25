package com.oriole.wisepen.user.api.domain.dto.res;


import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class GroupMemberTokenDetailResponse {
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "小组 ID")
	Long groupId;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "小组展示信息")
	GroupDisplayBase groupDisplayBase;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已使用 Token")
	Integer tokenUsed;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Token 限额")
	Integer tokenLimit;
}
