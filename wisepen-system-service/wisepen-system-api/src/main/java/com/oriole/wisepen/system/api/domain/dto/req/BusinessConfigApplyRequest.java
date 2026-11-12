package com.oriole.wisepen.system.api.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BusinessConfigApplyRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String revision;

    @NotNull
    private Object value;
}
