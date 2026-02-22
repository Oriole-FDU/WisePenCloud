package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.base.TagSpaceBase;
import com.oriole.wisepen.resource.enums.VisibilityModeEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagCreateRequest extends TagSpaceBase {
    private String parentId;

    @NotBlank(message = ResourceValidationMsg.TAG_NAME_NOT_BLANK)
    private String tagName;

    private String tagDesc;

    private VisibilityModeEnum visibilityMode;
    private List<String> specifiedUsers;
}