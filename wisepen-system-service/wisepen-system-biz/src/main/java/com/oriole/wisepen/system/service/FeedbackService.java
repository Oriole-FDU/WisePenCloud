package com.oriole.wisepen.system.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;

public interface FeedbackService {

    void createFeedback(Long userId, FeedbackRequest feedbackRequest);

    PageR<FeedbackEntity> listMyFeedback(Long userId, int page, int size,
                                         FeedbackStatus status, FeedbackType type);

    FeedbackEntity getMyFeedbackDetail(Long userId, Long feedbackId);

    PageR<FeedbackEntity> listFeedback(int page, int size,
                                       FeedbackStatus status, FeedbackType type);

    FeedbackEntity getFeedbackDetail(Long feedbackId);

    void updateFeedbackStatus(Long feedbackId, FeedbackStatus status);
}
