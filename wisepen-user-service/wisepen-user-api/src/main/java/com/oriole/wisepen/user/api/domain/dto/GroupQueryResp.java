package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GroupQueryResp implements Serializable {
	private Long id;
	private String name;
	private Long ownerId;
	private String description;
	private Integer type;
	private String coverUrl;
	private String inviteCode;
	private Integer memberCount;
}
