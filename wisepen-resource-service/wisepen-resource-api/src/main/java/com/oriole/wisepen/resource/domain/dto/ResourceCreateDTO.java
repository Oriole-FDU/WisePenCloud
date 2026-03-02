package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ResourceCreateDTO {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_NAME_NOT_BLANK)
    private String resourceName;
    @NotBlank(message = ResourceValidationMsg.RESOURCE_TYPE_NOT_BLANK)
    private String resourceType;
    @NotBlank(message = ResourceValidationMsg.OWNER_ID_NOT_BLANK)
    private String ownerId;

    private String preview;        // 初始预览图
    private Long size;             // 初始大小/字数
}