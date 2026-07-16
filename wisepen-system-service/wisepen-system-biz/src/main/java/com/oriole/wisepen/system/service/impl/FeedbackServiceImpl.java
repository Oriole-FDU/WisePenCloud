package com.oriole.wisepen.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackRequest;
import com.oriole.wisepen.system.api.domain.dto.req.FeedbackSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.req.QuestionnaireSubmitRequest;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackResponse;
import com.oriole.wisepen.system.api.domain.dto.resp.FeedbackDetailResponse;
import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.OperationType;
import com.oriole.wisepen.system.domain.entity.FeedbackEntity;
import com.oriole.wisepen.system.domain.entity.FeedbackStatusHistoryEntity;
import com.oriole.wisepen.system.mapper.FeedbackMapper;
import com.oriole.wisepen.system.mapper.FeedbackStatusHistoryMapper;
import com.oriole.wisepen.system.service.FeedbackService;
import com.oriole.wisepen.system.service.FeedbackStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 反馈服务实现
 * 
 * @author Xiong Heng
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final FeedbackStatusHistoryMapper feedbackStatusHistoryMapper;
    private final FeedbackStatusService feedbackStatusService;

    @Override
    @Transactional
    public void createFeedback(Long userId, FeedbackRequest feedbackRequest) {
        // 原有逻辑（向后兼容）
        FeedbackEntity feedbackEntity = BeanUtil.copyProperties(feedbackRequest, FeedbackEntity.class);
        feedbackEntity.setUserId(userId);
        feedbackEntity.setContent(feedbackRequest.getContent().trim());
        feedbackEntity.setContact(feedbackRequest.getContact().trim());
        if (feedbackRequest.getImageUrl() != null) {
            feedbackEntity.setImageUrl(feedbackRequest.getImageUrl().trim());
        }
        feedbackMapper.insert(feedbackEntity);
        log.info("反馈创建成功: userId={}, feedbackId={}", userId, feedbackEntity.getId());
    }
    
    /**
     * 提交简单反馈（新增方法）
     */
    public FeedbackResponse submitFeedback(Long userId, FeedbackSubmitRequest request) {
        // 校验：content 非空且长度 >= 10
        if (request.getContent() == null || request.getContent().trim().length() < 10) {
            throw new IllegalArgumentException("反馈内容长度至少 10 字");
        }
        
        FeedbackEntity entity = new FeedbackEntity();
        entity.setUserId(userId);
        entity.setTitle(request.getTitle());
        entity.setContent(request.getContent().trim());
        entity.setContact(request.getContact() != null ? request.getContact().trim() : null);
        entity.setFeedbackType(request.getFeedbackType());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 2);
        entity.setStatus("PENDING");
        
        feedbackMapper.insert(entity);
        
        // 创建状态历史记录
        createStatusHistory(entity.getId(), null, "PENDING", "反馈创建", userId, OperationType.CREATED.getValue());
        
        log.info("简单反馈提交成功: userId={}, feedbackId={}, type={}", 
            userId, entity.getId(), request.getFeedbackType());
        
        return buildFeedbackResponse(entity);
    }
    
    /**
     * 通过问卷提交反馈（新增方法）
     */
    public FeedbackResponse submitQuestionnaireFeedback(Long userId, QuestionnaireSubmitRequest request) {
        // TODO: 校验问卷存在性、答案校验
        
        FeedbackEntity entity = new FeedbackEntity();
        entity.setUserId(userId);
        entity.setTitle(request.getTitle());
        entity.setContact(request.getContact() != null ? request.getContact().trim() : null);
        entity.setFeedbackType(request.getFeedbackType());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 2);
        entity.setStatus("PENDING");
        entity.setQuestionnaireId(request.getQuestionnaireId());
        
        feedbackMapper.insert(entity);
        
        // TODO: 保存问卷答案到 feedback_answer 表
        
        createStatusHistory(entity.getId(), null, "PENDING", "问卷反馈创建", userId, OperationType.CREATED.getValue());
        
        log.info("问卷反馈提交成功: userId={}, feedbackId={}, questionnaireId={}", 
            userId, entity.getId(), request.getQuestionnaireId());
        
        return buildFeedbackResponse(entity);
    }
    
    /**
     * 查询用户个人反馈列表
     */
    public Page<FeedbackResponse> getUserFeedbacks(Long userId, int page, int size, String status) {
        LambdaQueryWrapper<FeedbackEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FeedbackEntity::getUserId, userId);
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq(FeedbackEntity::getStatus, status);
        }
        queryWrapper.orderByDesc(FeedbackEntity::getCreateTime);
        
        Page<FeedbackEntity> entityPage = feedbackMapper.selectPage(new Page<>(page, size), queryWrapper);
        
        Page<FeedbackResponse> responsePage = new Page<>();
        responsePage.setRecords(
            entityPage.getRecords().stream()
                .map(this::buildFeedbackResponse)
                .collect(java.util.stream.Collectors.toList())
        );
        responsePage.setTotal(entityPage.getTotal());
        responsePage.setSize(entityPage.getSize());
        responsePage.setCurrent(entityPage.getCurrent());
        
        return responsePage;
    }
    
    /**
     * 查询反馈详情
     */
    public FeedbackDetailResponse getFeedbackDetail(Long feedbackId, Long userId) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null) {
            throw new IllegalArgumentException("反馈不存在");
        }
        
        // 权限校验：用户只能查看自己的反馈
        if (!entity.getUserId().equals(userId)) {
            throw new SecurityException("无权限查看此反馈");
        }
        
        FeedbackDetailResponse response = new FeedbackDetailResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setContent(entity.getContent());
        response.setFeedbackType(entity.getFeedbackType());
        response.setStatus(entity.getStatus());
        response.setPriority(entity.getPriority());
        response.setContact(entity.getContact());
        response.setQuestionnaireId(entity.getQuestionnaireId());
        response.setCreatedAt(entity.getCreateTime());
        response.setUpdatedAt(entity.getUpdateTime());
        
        // TODO: 加载问卷回答、附件、状态历史
        
        return response;
    }
    
    /**
     * 更新反馈状态（管理员操作）
     */
    @Transactional
    public void updateFeedbackStatus(Long feedbackId, String newStatus, String reason, Long operatedById) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null) {
            throw new IllegalArgumentException("反馈不存在");
        }
        
        String oldStatus = entity.getStatus();
        
        // 校验状态流转合法性
        if (!feedbackStatusService.isValidTransition(oldStatus, newStatus)) {
            throw new IllegalArgumentException(
                String.format("无效的状态流转: %s -> %s", oldStatus, newStatus)
            );
        }
        
        entity.setStatus(newStatus);
        if ("RESOLVED".equals(newStatus)) {
            entity.setResolvedAt(java.time.LocalDateTime.now());
        } else if ("CLOSED".equals(newStatus)) {
            entity.setClosedAt(java.time.LocalDateTime.now());
        }
        
        feedbackMapper.updateById(entity);
        
        // 记录状态历史
        createStatusHistory(feedbackId, oldStatus, newStatus, reason, operatedById, OperationType.STATUS_CHANGED.getValue());
        
        log.info("反馈状态已更新: feedbackId={}, oldStatus={}, newStatus={}, operatedBy={}", 
            feedbackId, oldStatus, newStatus, operatedById);
    }
    
    /**
     * 指派反馈给处理人
     */
    @Transactional
    public void assignFeedback(Long feedbackId, Long assignedToId, Long operatedById) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null) {
            throw new IllegalArgumentException("反馈不存在");
        }
        
        entity.setAssignedToId(assignedToId);
        feedbackMapper.updateById(entity);
        
        // 记录操作历史
        createStatusHistory(feedbackId, entity.getStatus(), entity.getStatus(), 
            "分配给用户 " + assignedToId, operatedById, OperationType.ASSIGNED.getValue());
        
        log.info("反馈已分配: feedbackId={}, assignedToId={}, operatedBy={}", 
            feedbackId, assignedToId, operatedById);
    }
    
    /**
     * 创建状态历史记录
     */
    private void createStatusHistory(Long feedbackId, String oldStatus, String newStatus, 
                                    String reason, Long operatedById, String operationType) {
        FeedbackStatusHistoryEntity history = new FeedbackStatusHistoryEntity();
        history.setFeedbackId(feedbackId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setReason(reason);
        history.setOperatedById(operatedById);
        history.setOperationType(operationType);
        
        feedbackStatusHistoryMapper.insert(history);
    }
    
    /**
     * 构建反馈简单响应
     */
    private FeedbackResponse buildFeedbackResponse(FeedbackEntity entity) {
        FeedbackResponse response = new FeedbackResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setFeedbackType(entity.getFeedbackType());
        response.setStatus(entity.getStatus());
        response.setPriority(entity.getPriority());
        response.setCreatedAt(entity.getCreateTime());
        response.setUpdatedAt(entity.getUpdateTime());
        return response;
    }
}
