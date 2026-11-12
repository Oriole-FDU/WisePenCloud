package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ResourceAiBillingApprovalUpdateRequest {

    @NotBlank
    private String resourceId;

    @NotBlank
    @Pattern(regexp = "\\d+")
    private String groupId;

    @NotNull
    private Boolean approved;
}
