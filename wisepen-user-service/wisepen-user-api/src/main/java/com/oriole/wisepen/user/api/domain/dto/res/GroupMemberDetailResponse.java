package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupMemberBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupMemberDetailResponse extends GroupMemberBase {
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "小组 ID")
	private Long groupId; // 组ID

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "成员用户 ID")
	private Long memberId; // 成员 UID

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "成员展示信息")
	private UserDisplayBase memberInfo; // 成员信息
}
