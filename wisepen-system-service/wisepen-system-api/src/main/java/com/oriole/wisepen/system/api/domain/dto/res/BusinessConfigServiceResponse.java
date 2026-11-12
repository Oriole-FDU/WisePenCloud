package com.oriole.wisepen.system.api.domain.dto.res;

import lombok.Data;

import java.util.List;

@Data
public class BusinessConfigServiceResponse {
    private String service;
    private String revision;
    private List<BusinessConfigResponse> configs;
}
