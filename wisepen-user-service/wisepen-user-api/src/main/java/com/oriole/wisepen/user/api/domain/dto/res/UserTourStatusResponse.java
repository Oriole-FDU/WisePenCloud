package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.TourCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserTourStatusResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "引导编码")
    private TourCode tourCode;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否已完成")
    private Boolean completed;
}
