package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.cache.RedisCacheManager;
import com.oriole.wisepen.resource.domain.dto.req.ResourceRateRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceLikeRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceReadRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceUserInteractionRecordResponse;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceUserInteractionRecordEntity;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.CustomResourceItemRepository;
import com.oriole.wisepen.resource.repository.CustomResourceUserInteractionRecordRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.repository.ResourceUserInteractionRecordRepository;
import com.oriole.wisepen.resource.service.IResourceInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceInteractionServiceImpl implements IResourceInteractionService {

    private final ResourceItemRepository resourceItemRepository;
    private final ResourceUserInteractionRecordRepository resourceUserInteractRecordRepository;
    private final CustomResourceItemRepository customResourceItemRepository;
    private final CustomResourceUserInteractionRecordRepository customResourceUserInteractionRecordRepository;

    private final RedisCacheManager redisCacheManager;

    @Override
    public ResourceUserInteractionRecordResponse getResourceUserInteractionInfo(String resourceId, String userId) {
        ResourceUserInteractionRecordEntity interactionRecord = resourceUserInteractRecordRepository
                .findByUserIdAndResourceId(userId, resourceId)
                .orElseGet(() -> new ResourceUserInteractionRecordEntity(resourceId, userId));
        return BeanUtil.copyProperties(interactionRecord, ResourceUserInteractionRecordResponse.class);
    }

    @Override
    public void changeResourceReadStatus(ResourceReadRequest request, String userId) {
        String resourceId = request.getResourceId();
        // 软删除资源对用户不可见，拒绝记录互动
        ResourceItemEntity resource = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.RESOURCE_NOT_FOUND));
        if (resource.getDeletedAt() != null) throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        // 检查是否在窗口期中的第一次阅读
        Boolean isFirstReadInWindow = redisCacheManager.isFirstReadInWindow(resourceId, userId);
        if (Boolean.TRUE.equals(isFirstReadInWindow)) {
            customResourceUserInteractionRecordRepository.findAndSetRead(resourceId, userId, true);
            customResourceItemRepository.updateReadCount(resourceId, 1);
            log.info("resource read count incremented. resourceId={} userId={}", resourceId, userId);
        }
    }

    @Override
    public void changeResourceLikeStatus(ResourceLikeRequest request, String userId) {
        String resourceId = request.getResourceId();
        // 软删除资源对用户不可见，拒绝记录互动
        ResourceItemEntity resource = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.RESOURCE_NOT_FOUND));
        if (resource.getDeletedAt() != null) {
            throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        }
        boolean currentLiked = resourceUserInteractRecordRepository
                .findByUserIdAndResourceId(userId, resourceId)
                .map(r -> Boolean.TRUE.equals(r.getLiked()))
                .orElse(false);
        boolean wantLiked = !currentLiked;
        customResourceUserInteractionRecordRepository.findAndSetLiked(resourceId, userId, wantLiked);
        customResourceItemRepository.updateLikeCount(resourceId, wantLiked ? 1 : -1);
        log.info("resource like toggled. resourceId={} userId={} wantLiked={}", resourceId, userId, wantLiked);
    }

    @Override
    public void changeResourceScore(ResourceRateRequest request, String userId) {
        String resourceId = request.getResourceId();
        // 软删除资源对用户不可见，拒绝记录互动
        ResourceItemEntity resource = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.RESOURCE_NOT_FOUND));
        if (resource.getDeletedAt() != null) {
            throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);
        }
        Integer newScore = request.getScore();
        ResourceUserInteractionRecordEntity oldRecord =
                customResourceUserInteractionRecordRepository.findAndSetScore(resourceId, userId, newScore);
        Integer oldScore = oldRecord == null ? null : oldRecord.getScore();
        if (oldScore == null) { // 首次评分
            customResourceItemRepository.updateScore(resourceId, 1, newScore);
        } else if (!oldScore.equals(newScore)) { // 改分
            customResourceItemRepository.updateScore(resourceId, 0, newScore - oldScore);
        }
        log.info("resource rating changed. resourceId={} userId={} oldScore={} newScore={}",
                resourceId, userId, oldScore, newScore);
    }
}


