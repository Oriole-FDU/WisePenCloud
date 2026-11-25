package com.oriole.wisepen.system.api.domain.dto.res;

import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class FeedbackListItemResponse {
    private Long feedbackId;

    private Long userId;

    private Set<FeedbackType> type;

    private FeedbackStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
