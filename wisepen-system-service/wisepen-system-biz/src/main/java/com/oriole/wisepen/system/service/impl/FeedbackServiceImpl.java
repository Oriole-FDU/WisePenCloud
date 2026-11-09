package com.oriole.wisepen.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackQueryRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackStatusChangeRequest;
import com.oriole.wisepen.system.api.domain.dto.res.FeedbackDetailResponse;
import com.oriole.wisepen.system.api.domain.dto.res.FeedbackListItemResponse;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.mapper.FeedbackMapper;
import com.oriole.wisepen.system.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    public void createFeedback(Long userId, FeedbackRequest feedbackRequest) {
        FeedbackEntity feedbackEntity = BeanUtil.copyProperties(feedbackRequest, FeedbackEntity.class);
        feedbackEntity.setUserId(userId);
        // 业务默认值赋值
        feedbackEntity.setStatus(FeedbackStatus.PENDING);
        feedbackMapper.insert(feedbackEntity);
    }

    @Override
    public PageR<FeedbackListItemResponse> listFeedbacks(FeedbackQueryRequest request) {
        boolean filterByType = request.getType() != null && !request.getType().isEmpty();
        IPage<FeedbackEntity> feedbackPage = feedbackMapper.selectPage(new Page<>(request.getPage(), request.getSize()),
                Wrappers.<FeedbackEntity>lambdaQuery()
                        .select(FeedbackEntity::getId, FeedbackEntity::getUserId,
                                FeedbackEntity::getType,
                                FeedbackEntity::getStatus, FeedbackEntity::getCreateTime, FeedbackEntity::getUpdateTime)
                        .and(filterByType, typeQuery -> request.getType().forEach(type ->
                                typeQuery.or().apply("FIND_IN_SET({0}, type) > 0", type.getValue())))
                        .eq(request.getStatus() != null, FeedbackEntity::getStatus, request.getStatus())
                        .eq(request.getUserId() != null, FeedbackEntity::getUserId, request.getUserId())
                        .ge(request.getStartTime() != null, FeedbackEntity::getCreateTime, request.getStartTime())
                        .le(request.getEndTime() != null, FeedbackEntity::getCreateTime, request.getEndTime())
                        .orderByDesc(FeedbackEntity::getCreateTime, FeedbackEntity::getId));

        PageR<FeedbackListItemResponse> response = new PageR<>(feedbackPage.getTotal(), request.getPage(), request.getSize());
        response.addAll(feedbackPage.getRecords().stream().map(feedback -> {
            FeedbackListItemResponse item = BeanUtil.copyProperties(feedback, FeedbackListItemResponse.class);
            item.setFeedbackId(feedback.getId());
            return item;
        }).toList());
        return response;
    }

    @Override
    public FeedbackDetailResponse getFeedbackDetail(Long feedbackId) {
        FeedbackEntity feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) throw new ServiceException(SysError.FEEDBACK_NOT_FOUND);

        FeedbackDetailResponse response = BeanUtil.copyProperties(feedback, FeedbackDetailResponse.class);
        response.setFeedbackId(feedback.getId());
        return response;
    }

    @Override
    public void changeStatus(FeedbackStatusChangeRequest request) {
        FeedbackEntity feedback = feedbackMapper.selectById(request.getFeedbackId());
        if (feedback == null) throw new ServiceException(SysError.FEEDBACK_NOT_FOUND);
        if (feedback.getStatus() == request.getStatus()) return;

        int updated = feedbackMapper.update(null, Wrappers.<FeedbackEntity>lambdaUpdate()
                .eq(FeedbackEntity::getId, request.getFeedbackId())
                .set(FeedbackEntity::getStatus, request.getStatus())
                .set(FeedbackEntity::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) throw new ServiceException(SysError.FEEDBACK_NOT_FOUND);

        log.info("feedback status changed. feedbackId={} from={} to={}",
                feedback.getId(), feedback.getStatus(), request.getStatus());
    }
}
