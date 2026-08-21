package com.oriole.wisepen.user.api.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenConsumptionMessage implements Serializable{
	private Long userId;
	private Long groupId;
	private Integer usageTokens;
	private Integer billableTokens;
	private Integer billingRatio;
	private String traceId;
	private String modelName;
	private OffsetDateTime requestTime;
}
