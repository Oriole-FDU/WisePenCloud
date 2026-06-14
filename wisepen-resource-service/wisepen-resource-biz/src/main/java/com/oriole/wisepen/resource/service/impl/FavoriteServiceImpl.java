package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.domain.dto.req.FavoriteCollectionCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.FavoriteCollectionDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.FavoriteCollectionInfoUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceFavoriteRequest;
import com.oriole.wisepen.resource.domain.dto.res.FavoriteCollectionResponse;
import com.oriole.wisepen.resource.domain.dto.res.FavoriteItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.entity.FavoriteCollectionEntity;
import com.oriole.wisepen.resource.domain.entity.FavoriteResourceRef;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.CustomResourceItemRepository;
import com.oriole.wisepen.resource.repository.CustomFavoriteCollectionRepository;
import com.oriole.wisepen.resource.repository.FavoriteCollectionRepository;
import com.oriole.wisepen.resource.repository.FavoriteResourceRefRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.service.IFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FavoriteServiceImpl implements IFavoriteService {

    private final FavoriteCollectionRepository favoriteCollectionRepository;
    private final FavoriteResourceRefRepository favoriteResourceRefRepository;
    private final CustomFavoriteCollectionRepository customFavoriteCollectionRepository;
    private final CustomResourceItemRepository customResourceItemRepository;
    private final ResourceItemRepository resourceItemRepository;

    @Override
    @Transactional
    public void changeResourceFavoriteStatus(ResourceFavoriteRequest request, String userId) {
        String resourceId = request.getResourceId();
        // 检查资源是否存在
        ResourceItemEntity targetResource = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.RESOURCE_NOT_FOUND));
        if (targetResource.getDeletedAt() != null) throw new ServiceException(ResourceError.RESOURCE_NOT_FOUND);

        // 取消收藏
        if (Boolean.FALSE.equals(request.getFavorite())) {
            // 查找收藏记录
            favoriteResourceRefRepository.findFirstByUserIdAndResourceId(userId, resourceId).ifPresent(ref -> {
                // 移除资源收藏记录
                favoriteResourceRefRepository.delete(ref);
                // 扣减收藏次数（收藏夹与资源收藏计数）
                customFavoriteCollectionRepository.updateItemCount(ref.getCollectionIds(), -1);
                customResourceItemRepository.updateFavoriteCount(resourceId, -1);
                log.info("resource favorite removed. resourceId={} userId={}", resourceId, userId);
            });
            return;
        }

        Set<String> collectionIds = normalizeCollectionIds(request.getCollectionIds());
        // 无收藏夹列表，兜底为默认收藏夹
        if (collectionIds.isEmpty()) {
            collectionIds = Set.of(getDefaultCollection(userId).getCollectionId());
        }

        // 列出收藏夹
        List<FavoriteCollectionEntity> collections =
                favoriteCollectionRepository.findByCollectionIdInAndUserId(collectionIds, userId);

        // 检查收藏夹ID有效性
        Set<String> foundIds = collections.stream()
                .map(FavoriteCollectionEntity::getCollectionId)
                .collect(Collectors.toSet());
        if (!foundIds.containsAll(collectionIds)) {
            throw new ServiceException(ResourceError.FAVORITE_COLLECTION_NOT_FOUND);
        }

        // 查找收藏记录
        FavoriteResourceRef ref = favoriteResourceRefRepository.findFirstByUserIdAndResourceId(userId, resourceId).orElse(null);
        if (ref == null) { // 为空时新建
            favoriteResourceRefRepository.save(FavoriteResourceRef.builder()
                    .userId(userId).resourceId(resourceId)
                    .collectionIds(collectionIds.stream().toList()).build()
            );
            // 增加收藏次数（收藏夹与资源收藏计数）
            customFavoriteCollectionRepository.updateItemCount(collectionIds, 1);
            customResourceItemRepository.updateFavoriteCount(resourceId, 1);
            log.info("resource favorite created. resourceId={} userId={} collectionCount={}", resourceId, userId, collectionIds.size());
            return;
        }

        // 确定新增和删除的收藏夹
        List<String> oldCollectionIds = ref.getCollectionIds();
        List<String> newCollectionIds = collectionIds.stream().toList();
        List<String> addedCollectionIds = newCollectionIds.stream().filter(id -> !oldCollectionIds.contains(id)).toList();
        List<String> removedCollectionIds = oldCollectionIds.stream().filter(id -> !newCollectionIds.contains(id)).toList();

        if (addedCollectionIds.isEmpty() && removedCollectionIds.isEmpty()) return; // 无变动，直接返回

        // 保存变更
        ref.setCollectionIds(collectionIds.stream().toList());
        if (!addedCollectionIds.isEmpty()) ref.setFavoritedAt(LocalDateTime.now());
        favoriteResourceRefRepository.save(ref);

        // 变更收藏次数（收藏夹计数）
        customFavoriteCollectionRepository.updateItemCount(addedCollectionIds, 1);
        customFavoriteCollectionRepository.updateItemCount(removedCollectionIds, -1);
        log.info("resource favorite updated. resourceId={} userId={} collectionCount={}", resourceId, userId, collectionIds.size());
    }

    private Set<String> normalizeCollectionIds(List<String> collectionIds) {
        if (CollectionUtils.isEmpty(collectionIds)) return new LinkedHashSet<>();
        return collectionIds.stream().filter(StringUtils::hasText).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private FavoriteCollectionEntity getDefaultCollection(String userId) {
        return favoriteCollectionRepository.findFirstByUserIdAndIsDefaultTrue(userId).orElseGet(() -> {
            FavoriteCollectionEntity newCollection = new FavoriteCollectionEntity(userId, null, null, true);
            FavoriteCollectionEntity saved = favoriteCollectionRepository.save(newCollection);
            log.info("default favorite collection created. collectionId={} userId={}", saved.getCollectionId(), userId);
            return saved;
        });
    }

    private FavoriteCollectionEntity getCollectionInfo(String collectionId, String userId) {
        if (!StringUtils.hasText(collectionId)) return getDefaultCollection(userId);
        return favoriteCollectionRepository.findFirstByCollectionIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new ServiceException(ResourceError.FAVORITE_COLLECTION_NOT_FOUND));
    }

    @Override
    public String createCollection(FavoriteCollectionCreateRequest request, String userId) {
        FavoriteCollectionEntity entity = new FavoriteCollectionEntity(
                userId, request.getCollectionName(), request.getDescription(), false);
        String newCollectionId = favoriteCollectionRepository.save(entity).getCollectionId();
        log.info("favorite collection created. collectionId={} userId={}", newCollectionId, userId);
        return newCollectionId;
    }

    @Override
    public void updateCollectionInfo(FavoriteCollectionInfoUpdateRequest request, String userId) {
        FavoriteCollectionEntity entity = getCollectionInfo(request.getCollectionId(), userId);
        entity.setCollectionName(request.getCollectionName());
        entity.setDescription(request.getDescription());
        favoriteCollectionRepository.save(entity);
        log.info("favorite collection updated. collectionId={} userId={}", request.getCollectionId(), userId);
    }

    @Override
    @Transactional
    public void deleteCollection(FavoriteCollectionDeleteRequest request, String userId) {
        FavoriteCollectionEntity entity = getCollectionInfo(request.getCollectionId(), userId);
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            throw new ServiceException(ResourceError.DEFAULT_COLLECTION_CANNOT_DELETE);
        }
        // 查询受影响的资源
        List<FavoriteResourceRef> affectedRefs =
                favoriteResourceRefRepository.findByUserIdAndCollectionId(userId, request.getCollectionId());

        String defaultCollectionId = null;
        int movedToDefaultCount = 0; // 默认收藏夹移入计数
        List<FavoriteResourceRef> deletedRefs = new ArrayList<>();
        // 处理受影响的资源
        for (FavoriteResourceRef ref: affectedRefs) {
            List<String> collectionIds = ref.getCollectionIds().stream().filter(StringUtils::hasText)
                    .filter(id -> !request.getCollectionId().equals(id)).collect(Collectors.toList());
            // 收藏夹为空
            if (collectionIds.isEmpty()) {
                if (!request.getKeepResourcesToDefault()) deletedRefs.add(ref); // 直接删除，不保留
                else { // 保留至默认收藏夹
                    if (defaultCollectionId == null) defaultCollectionId = getDefaultCollection(userId).getCollectionId();
                    collectionIds.add(defaultCollectionId);
                    movedToDefaultCount++; // 默认收藏夹移入计数增加
                }
            }
            ref.setCollectionIds(collectionIds);
        }

        // 处理受影响的资源
        if (!affectedRefs.isEmpty()) {
            favoriteResourceRefRepository.saveAll(affectedRefs);
            // 处理默认收藏夹移入计数
            if (movedToDefaultCount > 0) {
                customFavoriteCollectionRepository.updateItemCount(Collections.singletonList(defaultCollectionId), movedToDefaultCount);
            }
            if (!deletedRefs.isEmpty()) { // 如果有移除资源
                favoriteResourceRefRepository.deleteAll(deletedRefs);
                // 删除时扣减资源的收藏统计
                customResourceItemRepository.updateFavoriteCount(deletedRefs.stream().map(FavoriteResourceRef::getResourceId).toList(), -1);
            }
        }

        // 移除收藏夹
        favoriteCollectionRepository.deleteById(request.getCollectionId());

        log.info("favorite collection deleted. collectionId={} userId={} affectedRefCount={}",
                request.getCollectionId(), userId, affectedRefs.size());
    }

    @Override
    public List<FavoriteCollectionResponse> listCollections(String userId) {
        getDefaultCollection(userId); // 新建默认收藏夹
        List<FavoriteCollectionEntity> collections =
                favoriteCollectionRepository.findByUserIdOrderByIsDefaultDescCreateTimeDesc(userId);
        return collections.stream().map(entity -> BeanUtil.copyProperties(entity, FavoriteCollectionResponse.class)).toList();
    }

    @Override
    public PageR<FavoriteItemResponse> listFavoritedResources(String collectionId, int page, int size, String userId) {
        Pageable pageable =  PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("favoritedAt"), Sort.Order.desc("id")));
        Page<FavoriteResourceRef> refPage;
        if (collectionId != null){
            getCollectionInfo(collectionId, userId);
            refPage = favoriteResourceRefRepository.findByUserIdAndCollectionId(userId, collectionId, pageable);
        } else {
            refPage = favoriteResourceRefRepository.findByUserId(userId, pageable);
        }

        List<String> resourceIds = refPage.getContent().stream()
                .map(FavoriteResourceRef::getResourceId).filter(StringUtils::hasText).toList();

        Map<String, ResourceItemEntity> resourceMap = new HashMap<>();
        // 批量查询 resourceItem
        if (!resourceIds.isEmpty()) {
            resourceItemRepository.findAllById(resourceIds).forEach(entity -> resourceMap.put(entity.getResourceId(), entity));
        }

        List<FavoriteItemResponse> responses = refPage.getContent().stream()
                .map(ref -> {
                    FavoriteItemResponse favoriteItemResponse = BeanUtil.copyProperties(ref, FavoriteItemResponse.class);
                    ResourceItemEntity resourceItemEntity = resourceMap.get(ref.getResourceId());
                    if (resourceItemEntity == null || resourceItemEntity.getDeletedAt() != null) {
                        favoriteItemResponse.setAccessible(false);
                        return favoriteItemResponse;
                    }
                    favoriteItemResponse.setAccessible(true);
                    favoriteItemResponse.setResourceInfo(BeanUtil.copyProperties(resourceItemEntity, ResourceItemResponse.class));
                    return favoriteItemResponse;
                }).toList();

        PageR<FavoriteItemResponse> pageR = new PageR<>(refPage.getTotalElements(), page, size);
        pageR.addAll(responses);
        return pageR;
    }
}
