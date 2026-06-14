package com.oriole.wisepen.ai.asset.domain.dto.req;

import com.oriole.wisepen.ai.asset.constant.AIAssetValidationMsg;
import com.oriole.wisepen.ai.asset.enums.SkillSourceType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillCreateRequest {
    @NotBlank(message = AIAssetValidationMsg.TITLE_NOT_BLANK)
    private String title;

    private String name;

    private String description;

    @Builder.Default
    private SkillSourceType sourceType = SkillSourceType.MANUAL;
}
