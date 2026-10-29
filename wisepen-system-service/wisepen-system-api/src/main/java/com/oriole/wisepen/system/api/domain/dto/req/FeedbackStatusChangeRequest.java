package com.oriole.wisepen.system.api.domain.dto.req;

import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FeedbackStatusChangeRequest {
    @NotNull
    @Positive
    @Schema(description = "反馈 ID", requiredMode = Schema.RequiredMode.REQUIRED, type = "string")
    private Long feedbackId;

    @NotNull
    @Schema(description = "目标状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private FeedbackStatus status;
}
