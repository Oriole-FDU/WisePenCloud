package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.base.TagSpaceBase;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagDeleteRequest extends TagSpaceBase {
    @NotBlank(message = ResourceValidationMsg.GROUP_ID_NOT_BLANK)
    private String targetTagId;
}
