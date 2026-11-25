package com.oriole.wisepen.system.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackQueryRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackStatusChangeRequest;
import com.oriole.wisepen.system.api.domain.dto.res.FeedbackDetailResponse;
import com.oriole.wisepen.system.api.domain.dto.res.FeedbackListItemResponse;

public interface FeedbackService {
    void createFeedback(Long userId, FeedbackRequest feedbackRequest);

    PageR<FeedbackListItemResponse> listFeedbacks(FeedbackQueryRequest request);

    FeedbackDetailResponse getFeedbackDetail(Long feedbackId);

    void changeStatus(FeedbackStatusChangeRequest request);
}
