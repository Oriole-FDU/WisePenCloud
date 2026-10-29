package com.oriole.wisepen.system.api.domain.dto.res;

import lombok.Data;

@Data
public class BusinessConfigSummaryResponse {
    private String code;
    private String name;
    private String description;
    private String service;
    private boolean restartRequired;
}
