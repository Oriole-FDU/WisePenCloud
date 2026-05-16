package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.AclGrantMode;
import com.oriole.wisepen.resource.enums.ResourceMountMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagUpdateRequest extends TagInfoBase {
    @NotBlank(message = ResourceValidationMsg.TAG_ID_NOT_BLANK)
    private String targetTagId;

    private AclGrantMode aclGrantMode;
    private ResourceMountMode resourceMountMode;
    private List<String> aclGrantSpecifiedUsers;
    private List<String> resourceMountSpecifiedUsers;
    private List<ResourceAction> grantedActions;
}
