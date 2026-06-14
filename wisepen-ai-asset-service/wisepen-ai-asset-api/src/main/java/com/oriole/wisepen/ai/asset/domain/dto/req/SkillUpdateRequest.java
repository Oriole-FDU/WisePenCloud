package com.oriole.wisepen.ai.asset.domain.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oriole.wisepen.ai.asset.constant.AIAssetValidationMsg;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillUpdateRequest {
    private String resourceId;

    private String name;

    private String description;

    @AssertTrue(message = AIAssetValidationMsg.RESOURCE_ID_NOT_BLANK)
    @JsonIgnore
    public boolean isResourceIdPresent() {
        return StringUtils.hasText(resourceId);
    }
}
