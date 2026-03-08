package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GetGroupMemberQuotasResp implements Serializable {
	Long userId;
	String realName;
	Integer quotaLimit;
	Integer quotaUsed;
}
