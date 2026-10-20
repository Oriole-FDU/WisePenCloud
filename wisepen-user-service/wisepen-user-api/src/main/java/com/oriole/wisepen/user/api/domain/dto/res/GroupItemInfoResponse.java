package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupItemInfoResponse extends GroupDisplayBase {
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "小组 ID")
	private Long groupId;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "创建者用户 ID")
	private Long ownerId;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "创建者展示信息")
	private UserDisplayBase ownerInfo;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "成员数")
	private Integer memberCount;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
	private LocalDateTime createTime;
}
