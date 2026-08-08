package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.base.TagSpaceBase;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagDeleteRequest extends TagSpaceBase {
    @NotEmpty(message = ResourceValidationMsg.TAG_IDS_NOT_EMPTY)
    private List<@NotBlank(message = ResourceValidationMsg.TAG_ID_NOT_BLANK) String> targetTagIds;
}
