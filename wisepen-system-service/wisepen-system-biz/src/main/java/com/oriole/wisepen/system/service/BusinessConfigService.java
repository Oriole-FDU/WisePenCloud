package com.oriole.wisepen.system.service;

import com.oriole.wisepen.system.api.domain.dto.req.BusinessConfigApplyRequest;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigApplyResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigServiceResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigSummaryResponse;

import java.util.List;

public interface BusinessConfigService {

    List<BusinessConfigSummaryResponse> listConfigs();

    BusinessConfigServiceResponse listServiceConfigs(String service);

    BusinessConfigApplyResponse applyConfig(BusinessConfigApplyRequest request);
}
