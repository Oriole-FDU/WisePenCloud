package com.oriole.wisepen.ai.asset.domain.dto.req;

import com.oriole.wisepen.ai.asset.constant.AIAssetValidationMsg;
import com.oriole.wisepen.ai.asset.domain.base.AgentSpecInfoBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpecUpdateRequest {
    @NotBlank(message = AIAssetValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    private Integer draftVersion;

    @NotNull(message = AIAssetValidationMsg.SPEC_NOT_NULL)
    private AgentSpecInfoBase spec;
}
