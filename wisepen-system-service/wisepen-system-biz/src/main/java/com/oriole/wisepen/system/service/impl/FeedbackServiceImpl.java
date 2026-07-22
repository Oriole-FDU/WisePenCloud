package com.oriole.wisepen.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.mapper.FeedbackMapper;
import com.oriole.wisepen.system.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Xiong Heng
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    public void createFeedback(Long userId, FeedbackRequest feedbackRequest) {
        FeedbackEntity feedbackEntity = BeanUtil.copyProperties(feedbackRequest, FeedbackEntity.class);
        feedbackEntity.setUserId(userId);
        feedbackEntity.setContent(feedbackRequest.getContent().trim());
        feedbackEntity.setContact(feedbackRequest.getContact().trim());
        feedbackEntity.setStatus(FeedbackStatus.PENDING);
        feedbackMapper.insert(feedbackEntity);
    }

    @Override
    public PageR<FeedbackEntity> listMyFeedback(Long userId, int page, int size,
                                                FeedbackStatus status, FeedbackType type) {
        LambdaQueryWrapper<FeedbackEntity> queryWrapper = Wrappers.<FeedbackEntity>lambdaQuery()
                .eq(FeedbackEntity::getUserId, userId)
                .eq(FeedbackEntity::getStatus, status)
                .orderByDesc(FeedbackEntity::getCreateTime);

        applyTypeFilter(queryWrapper, type);

        Page<FeedbackEntity> feedbackPage = feedbackMapper.selectPage(new Page<>(page, size), queryWrapper);
        PageR<FeedbackEntity> result = new PageR<>(feedbackPage.getTotal(), page, size);
        result.addAll(feedbackPage.getRecords());
        return result;
    }

    @Override
    public FeedbackEntity getMyFeedbackDetail(Long userId, Long feedbackId) {
        FeedbackEntity feedbackEntity = feedbackMapper.selectOne(
                Wrappers.<FeedbackEntity>lambdaQuery()
                        .eq(FeedbackEntity::getId, feedbackId)
                        .eq(FeedbackEntity::getUserId, userId)
        );
        if (feedbackEntity == null) {
            throw new ServiceException(SysError.FEEDBACK_NOT_FOUND);
        }
        return feedbackEntity;
    }

    @Override
    public PageR<FeedbackEntity> listFeedback(int page, int size,
                                              FeedbackStatus status, FeedbackType type) {
        LambdaQueryWrapper<FeedbackEntity> queryWrapper = Wrappers.<FeedbackEntity>lambdaQuery()
                .eq(FeedbackEntity::getStatus, status)
                .orderByDesc(FeedbackEntity::getCreateTime);

        applyTypeFilter(queryWrapper, type);

        Page<FeedbackEntity> feedbackPage = feedbackMapper.selectPage(new Page<>(page, size), queryWrapper);
        PageR<FeedbackEntity> result = new PageR<>(feedbackPage.getTotal(), page, size);
        result.addAll(feedbackPage.getRecords());
        return result;
    }

    @Override
    public FeedbackEntity getFeedbackDetail(Long feedbackId) {
        FeedbackEntity feedbackEntity = feedbackMapper.selectById(feedbackId);
        if (feedbackEntity == null) {
            throw new ServiceException(SysError.FEEDBACK_NOT_FOUND);
        }
        return feedbackEntity;
    }

    @Override
    public void updateFeedbackStatus(Long feedbackId, FeedbackStatus status) {
        FeedbackEntity feedbackEntity = new FeedbackEntity();
        feedbackEntity.setStatus(status);
        int updatedRows = feedbackMapper.update(
                feedbackEntity,
                Wrappers.<FeedbackEntity>lambdaUpdate()
                        .eq(FeedbackEntity::getId, feedbackId)
        );
        if (updatedRows == 0) {
            throw new ServiceException(SysError.FEEDBACK_NOT_FOUND);
        }
    }

    private void applyTypeFilter(LambdaQueryWrapper<FeedbackEntity> queryWrapper, FeedbackType type) {
        switch (type) {
            case BUG_REPORT -> queryWrapper.eq(FeedbackEntity::getBugReport, true);
            case SUGGESTION -> queryWrapper.eq(FeedbackEntity::getSuggestion, true);
            case CONSULTATION -> queryWrapper.eq(FeedbackEntity::getConsultation, true);
            case COMPLAINT -> queryWrapper.eq(FeedbackEntity::getComplaint, true);
            case OTHER -> queryWrapper.eq(FeedbackEntity::getOther, true);
        }
    }
}
