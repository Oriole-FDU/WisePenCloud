package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreateGroupReq implements Serializable {

	@NotBlank(message = "groupName 不能为空")
	private String groupName;

	@NotNull(message = "groupType 不能为空")
	private Integer groupType;

	@NotBlank(message = "description 不能为空")
	private String description;

	// 可选
	private String coverUrl;
}
