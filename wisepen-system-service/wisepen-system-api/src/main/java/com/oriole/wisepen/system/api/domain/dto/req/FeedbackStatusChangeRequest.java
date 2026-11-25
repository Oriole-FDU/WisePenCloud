package com.oriole.wisepen.system.api.domain.dto.req;

import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FeedbackStatusChangeRequest {
    @NotNull
    @Positive
    private Long feedbackId;

    @NotNull
    private FeedbackStatus status;
}
