package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.WalletTransactionRecordBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class WalletTransactionRecordResponse extends WalletTransactionRecordBase {
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "操作方展示信息")
	UserDisplayBase operatorDisplay;

	@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
	LocalDateTime createTime;
}
