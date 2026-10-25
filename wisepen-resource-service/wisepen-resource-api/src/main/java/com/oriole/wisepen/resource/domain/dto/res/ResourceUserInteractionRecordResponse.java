package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceUserInteractionRecordBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ResourceUserInteractionRecordResponse extends ResourceUserInteractionRecordBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;
}
