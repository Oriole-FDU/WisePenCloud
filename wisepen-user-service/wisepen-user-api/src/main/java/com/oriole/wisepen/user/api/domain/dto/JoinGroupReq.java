package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class JoinGroupReq implements Serializable {

	@NotBlank(message = "inviteCode 不能为空")
	private String inviteCode;
}