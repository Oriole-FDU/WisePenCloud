package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author Administrator
 */
@Data
public class KickGroupReq implements Serializable {

	@NotNull(message = "groupId 不能为空")
	private Long groupId;

	@NotEmpty(message = "targetUserIds 不能为空")
	private List<Long> targetUserIds;
}