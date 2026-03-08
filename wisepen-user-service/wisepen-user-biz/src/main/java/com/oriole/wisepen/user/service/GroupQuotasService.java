package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.dto.GetGroupMemberQuotasResp;
import com.oriole.wisepen.user.api.domain.dto.GetMyGroupQuotasResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;

import java.util.List;
import java.util.Map;

public interface GroupQuotasService {
	PageResp<GetGroupMemberQuotasResp> getGroupMemberQuotas(Long groupId, Integer page, Integer size);
	PageResp<GetMyGroupQuotasResp> getMyGroupQuotas();
	void UpdateMemberQuotas(Long groupId, List<Long> targetUserIds, Integer newLimit);
}
