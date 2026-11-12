package com.oriole.wisepen.system.api.domain.dto.res;

import lombok.Data;

import java.util.Map;

@Data
public class BusinessConfigResponse {
    private String code;
    private String name;
    private String description;
    private boolean restartRequired;
    private Map<String, Object> schema;
    private boolean exists;
    private Object value;
}
