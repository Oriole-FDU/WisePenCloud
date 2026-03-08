package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class QuitGroupReq implements Serializable {

	@NotNull(message = "groupId 不能为空")
	private Long groupId;
}