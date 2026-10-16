package com.oriole.wisepen.ai.asset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadCancelRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.entity.AIResourceBaseEntity;
import com.oriole.wisepen.ai.asset.domain.entity.VersionBundleBaseEntity;
import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import com.oriole.wisepen.ai.asset.exception.AIResourceError;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.repository.AIResourceBaseRepository;
import com.oriole.wisepen.ai.asset.repository.VersionBundleBaseRepository;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public abstract class VersionServiceImpl<VT extends VersionBundleBaseEntity<VT>, AT extends AIResourceBaseEntity<AT>> implements IVersionService<VT> {

    private static final String ROOT_PATH = "/";
    private static final int MAX_DRAFT_UPDATE_ATTEMPTS = 16;
    private static final int MAX_STORAGE_STATUS_BATCH_SIZE = 100;

    final VersionBundleBaseRepository<VT> versionBundleBaseRepository;
    final AIResourceBaseRepository<AT> aiResourceBaseRepository;
    private final RemoteStorageService remoteStorageService;
    private final AIAssetEventPublisher eventPublisher;

    protected abstract VT buildDraft(String resourceId, Integer draftVersion);

    protected abstract StorageSceneEnum getStorageScene();

    @Override
    public void createDraftVersion(String resourceId, Integer draftVersion) {
        if (versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, draftVersion).isPresent()) return;
        VT draft = buildDraft(resourceId, draftVersion);
        // 固定内部 ID 配合 insert，发布重试或并发完成时不会重建并覆盖下一草稿。
        draft.setId(resourceId + ":" + draftVersion);
        // 如果不是首份草案(1)需要复制此前的资源列表
        if (draftVersion > 1) {
            versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, draftVersion - 1).ifPresent(draft::copyBy);
        }
        try {
            versionBundleBaseRepository.insert(draft);
        } catch (DuplicateKeyException e) {
            if (versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, draftVersion).isEmpty()) throw e;
            log.warn("AI resource draft creation skipped. resourceId={} version={} reason=\"concurrent creation\"",
                    resourceId, draftVersion, e);
        }
    }

    @Override
    @Transactional
    public void forkPublishedVersionSnapshot(String sourceResourceId, Integer sourceVersion, String targetResourceId) {
        if (sourceVersion == null || sourceVersion <= 0) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND);
        }
        VT source = versionBundleBaseRepository.findByResourceIdAndVersion(sourceResourceId, sourceVersion)
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (source.getStatus() != VersionStatus.PUBLISHED) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND);
        }

        List<String> copiedObjectKeys = new ArrayList<>();
        try {
            VT published = buildDraft(targetResourceId, 1);
            published.copyBy(source);
            published.setStatus(VersionStatus.PUBLISHED);
            // 资产文件需要物理拷贝
            published.setAssets(copyAssetsForFork(source.getAssets(), targetResourceId, copiedObjectKeys));
            published.checkCoreAssetReady();
            versionBundleBaseRepository.save(published);

            VT draft = buildDraft(targetResourceId, 2);
            draft.copyBy(published);
            draft.setStatus(VersionStatus.DRAFT);
            versionBundleBaseRepository.save(draft);
        } catch (Exception e) {
            cleanupForkedVersion(targetResourceId, copiedObjectKeys);
            throw new ServiceException(AIResourceError.AI_RESOURCE_FORK_FAILED, e.getMessage());
        }
    }

    private List<AssetInfoBase> copyAssetsForFork(List<AssetInfoBase> sourceAssets, String targetResourceId, List<String> copiedObjectKeys) {
        if (sourceAssets == null || sourceAssets.isEmpty()) {
            return new ArrayList<>();
        }
        List<AssetInfoBase> copiedAssets = new ArrayList<>();
        for (AssetInfoBase sourceAsset : sourceAssets) {
            if (isAssetUnavailable(sourceAsset)) {
                throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_NOT_READY);
            }
            StorageRecordDTO copied = remoteStorageService.copyFile(CopyReqDTO.builder()
                    .sourceObjectKey(sourceAsset.getObjectKey())
                    .scene(getStorageScene())
                    .bizTag(targetResourceId)
                    .build()).getData();
            if (copied == null || !StringUtils.hasText(copied.getObjectKey())) {
                throw new ServiceException(AIResourceError.AI_RESOURCE_FORK_FAILED);
            }
            copiedObjectKeys.add(copied.getObjectKey());
            copiedAssets.add(AssetInfoBase.builder()
                    .id(IdUtil.fastSimpleUUID())
                    .path(sourceAsset.getPath())
                    .name(sourceAsset.getName())
                    .assetResourceType(sourceAsset.getAssetResourceType())
                    .objectKey(copied.getObjectKey())
                    .size(copied.getSize())
                    .uploadStatus(AssetUploadStatus.AVAILABLE)
                    .build());
        }
        return copiedAssets;
    }

    private void cleanupForkedVersion(String targetResourceId, List<String> copiedObjectKeys) {
        versionBundleBaseRepository.deleteByResourceIdIn(List.of(targetResourceId));
        if (copiedObjectKeys != null && !copiedObjectKeys.isEmpty()) {
            eventPublisher.publishFileDeleteEvent(copiedObjectKeys);
        }
    }

    @Override
    public VT getVersionBundle(String resourceId, Integer version) {
        // 如果未指定版本号则使用当前发布的版本号
        if (version == null){
            AT entity = aiResourceBaseRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_NOT_FOUND));
            version = entity.getVersion();
        }
        return versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, version)
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
    }

    @Override
    @Transactional
    public AssetUploadInitResponse initUploadAssets(AssetUploadInitRequest req) {
        // 检查当前是否是草案版本
        VT draft = versionBundleBaseRepository.findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_AI_RESOURCE_VERSION);

        List<AssetInfoBase> uploads = new ArrayList<>();

        AssetUploadInitResponse response = AssetUploadInitResponse.builder()
                .resourceId(req.getResourceId())
                .version(draft.getVersion())
                .build();

        try {
            for (AssetUploadInitRequest.AssetUploadRequest assetReq : req.getAssets()) {
                // 校验 Path 和 Name 合法性
                validateDirectoryPath(assetReq.getPath());
                validateFileName(assetReq.getName());

                UploadInitRespDTO uploadInitRespDTO;
                try {
                    uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                            .md5(assetReq.getMd5())
                            .extension(assetReq.getAssetResourceType().getExtension())
                            .scene(getStorageScene())
                            .bizTag(req.getResourceId())
                            .expectedSize(assetReq.getExpectedSize())
                            .isNeedCallback(true)
                            .build()).getData();
                } catch (Exception e) {
                    log.warn("AI resource asset upload init failed. resourceId={} version={} dependency=storageService", req.getResourceId(), req.getDraftVersion(), e);
                    throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_UPLOAD_URL_APPLY_FAILED);
                }

                AssetInfoBase asset = AssetInfoBase.builder().id(IdUtil.fastSimpleUUID())
                        .path(assetReq.getPath()).name(assetReq.getName()).assetResourceType(assetReq.getAssetResourceType())
                        .objectKey(uploadInitRespDTO.getObjectKey()).size(assetReq.getExpectedSize())
                        .uploadStatus(Boolean.TRUE.equals(uploadInitRespDTO.getFlashUploaded()) ? AssetUploadStatus.AVAILABLE : AssetUploadStatus.UPLOADING)
                        .build();
                uploads.add(asset);

                AssetUploadInitResponse.AssetUploadTicket assetUploadTicket = AssetUploadInitResponse.AssetUploadTicket
                        .builder().assetId(asset.getId())
                        .path(asset.getPath())
                        .name(asset.getName()).build();
                BeanUtil.copyProperties(uploadInitRespDTO, assetUploadTicket);
                response.getAssetUploadTickets().add(assetUploadTicket);
            }

            updateDraftAssets(req.getResourceId(), req.getDraftVersion(), current -> {
                for (AssetInfoBase upload : uploads) {
                    // 覆盖沿用文件 ID，但在成功之前不修改原文件的类型、大小和 objectKey。
                    Stream.concat(current.getAssets().stream(), current.getPendingAssets().stream())
                            .filter(asset -> sameFile(asset, upload)).findFirst()
                            .ifPresent(asset -> upload.setId(asset.getId()));
                    current.getPendingAssets().removeIf(asset -> sameFile(asset, upload));
                    if (upload.getUploadStatus() == AssetUploadStatus.AVAILABLE) {
                        current.getAssets().removeIf(asset -> sameFile(asset, upload));
                        current.getAssets().add(upload);
                    } else {
                        current.getPendingAssets().add(upload);
                    }
                }
            });
            for (int i = 0; i < uploads.size(); i++) {
                response.getAssetUploadTickets().get(i).setAssetId(uploads.get(i).getId());
            }
        } catch (Exception e) {
            // 批次中途失败时，已申请的秒传对象也可能没有进入版本文档。
            Set<String> abandonedKeys = new HashSet<>();
            uploads.forEach(upload -> abandonedKeys.add(upload.getObjectKey()));
            try {
                deleteUnreferencedObjectKeys(req.getResourceId(), abandonedKeys);
            } catch (Exception cleanupError) {
                log.warn("AI resource upload cleanup failed. resourceId={} version={}",
                        req.getResourceId(), req.getDraftVersion(), cleanupError);
            }
            throw e;
        }
        return response;
    }

    private void validateDirectoryPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/") || path.contains("\\")
                || path.contains("//") || path.contains("/../") || path.endsWith("/..")
                || (!ROOT_PATH.equals(path) && path.endsWith("/"))) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_PATH_INVALID);
        }
    }

    private void validateFileName(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_PATH_INVALID);
        }
    }

    private boolean sameFile(AssetInfoBase left, AssetInfoBase right) {
        return Objects.equals(left.getPath(), right.getPath()) && Objects.equals(left.getName(), right.getName());
    }

    private Set<String> objectKeys(VT version) {
        Set<String> keys = new HashSet<>();
        if (version.getAssets() != null) version.getAssets().forEach(asset -> keys.add(asset.getObjectKey()));
        if (version.getPendingAssets() != null) version.getPendingAssets().forEach(asset -> keys.add(asset.getObjectKey()));
        return keys;
    }

    private void updateDraftAssets(String resourceId, Integer version, Consumer<VT> change) {
        for (int attempt = 0; attempt < MAX_DRAFT_UPDATE_ATTEMPTS; attempt++) {
            VT draft = versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, version)
                    .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
            if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_AI_RESOURCE_VERSION);
            List<AssetInfoBase> previousAssets = draft.getAssets();
            List<AssetInfoBase> previousPendingAssets = draft.getPendingAssets();
            Set<String> removedKeys = objectKeys(draft);
            draft.setAssets(new ArrayList<>(previousAssets == null ? List.of() : previousAssets));
            draft.setPendingAssets(new ArrayList<>(previousPendingAssets == null ? List.of() : previousPendingAssets));
            change.accept(draft);
            if (Objects.equals(previousAssets, draft.getAssets())
                    && Objects.equals(previousPendingAssets, draft.getPendingAssets())) return;
            if (versionBundleBaseRepository.updateDraftAssets(draft.getId(), draft.getRevision(),
                    draft.getAssets(), draft.getPendingAssets(), LocalDateTime.now()) == 0) continue;
            removedKeys.removeAll(objectKeys(draft));
            deleteUnreferencedObjectKeys(resourceId, removedKeys);
            return;
        }
        throw new OptimisticLockingFailureException("AI resource draft update conflicted: " + resourceId + ":" + version);
    }

    @Override
    public void cancelUploadAssets(AssetUploadCancelRequest req) {
        updateDraftAssets(req.getResourceId(), req.getDraftVersion(), draft ->
                draft.getPendingAssets().removeIf(asset -> req.getObjectKeys().contains(asset.getObjectKey())));
    }

    @Override
    public void deleteAssets(AssetDeleteRequest req) {
        updateDraftAssets(req.getResourceId(), req.getDraftVersion(), draft -> {
            draft.getAssets().removeIf(asset -> req.getAssetIds().contains(asset.getId()));
            draft.getPendingAssets().removeIf(asset -> req.getAssetIds().contains(asset.getId()));
        });
    }

    private void deleteUnreferencedObjectKeys(String resourceId, Set<String> objectKeys) {
        if (objectKeys.isEmpty()) return;

        Set<String> referencedObjectKeys = new HashSet<>();
        versionBundleBaseRepository.findByResourceId(resourceId).forEach(version ->
                referencedObjectKeys.addAll(objectKeys(version)));

        List<String> deletableObjectKeys = objectKeys.stream()
                .filter(StringUtils::hasText)
                .filter(objectKey -> !referencedObjectKeys.contains(objectKey))
                .toList();

        if (deletableObjectKeys.isEmpty()) return;
        eventPublisher.publishFileDeleteEvent(deletableObjectKeys);
    }

    private void reconcileDraftUploadStatuses(String resourceId, Integer version) {
        VT draft = versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, version)
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) return;

        Set<String> uploadingKeys = new LinkedHashSet<>();
        if (draft.getPendingAssets() != null) {
            draft.getPendingAssets().forEach(asset -> uploadingKeys.add(asset.getObjectKey()));
        }
        if (draft.getAssets() != null) {
            draft.getAssets().stream().filter(asset -> asset.getUploadStatus() == AssetUploadStatus.UPLOADING)
                    .forEach(asset -> uploadingKeys.add(asset.getObjectKey()));
        }
        if (uploadingKeys.isEmpty()) return;

        List<String> objectKeys = new ArrayList<>(uploadingKeys);
        Map<String, StorageRecordDTO> records = new HashMap<>();
        for (int start = 0; start < objectKeys.size(); start += MAX_STORAGE_STATUS_BATCH_SIZE) {
            List<String> batch = objectKeys.subList(start,
                    Math.min(start + MAX_STORAGE_STATUS_BATCH_SIZE, objectKeys.size()));
            var response = remoteStorageService.getUploadRecords(batch);
            if (response == null || !Objects.equals(response.getCode(), 200) || response.getData() == null
                    || response.getData().stream().anyMatch(record -> record.getStatus() == null)) {
                throw new ServiceException(CommonError.INTERNAL_ERROR);
            }
            response.getData().forEach(record -> records.put(record.getObjectKey(), record));
        }
        Set<String> expiredKeys = new HashSet<>();
        for (String objectKey : uploadingKeys) {
            StorageRecordDTO stored = records.get(objectKey);
            if (stored == null || stored.getStatus() == StorageStatusEnum.DELETED) {
                expiredKeys.add(objectKey);
            } else if (stored.getStatus() == StorageStatusEnum.AVAILABLE) {
                handleFileUploaded(FileUploadedMessage.builder().scene(getStorageScene())
                        .objectKey(objectKey).size(stored.getSize()).build());
            }
        }
        if (!expiredKeys.isEmpty()) {
            updateDraftAssets(resourceId, version, current -> {
                current.getPendingAssets().removeIf(asset -> expiredKeys.contains(asset.getObjectKey()));
                current.getAssets().removeIf(asset -> asset.getUploadStatus() == AssetUploadStatus.UPLOADING
                        && expiredKeys.contains(asset.getObjectKey()));
            });
        }
    }

    @Override
    @Transactional
    public void publishVersion(String resourceId) {
        AT resourceEntity = aiResourceBaseRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_NOT_FOUND));

        int draftVersion = resourceEntity.getVersion() + 1;
        reconcileDraftUploadStatuses(resourceId, draftVersion);
        for (int attempt = 0; attempt < MAX_DRAFT_UPDATE_ATTEMPTS; attempt++) {
            VT draft = getVersionBundle(resourceId, draftVersion);
            // 上次可能已冻结版本，但尚未创建下一草稿或推进主版本号；重试继续完成。
            if (draft.getStatus() == VersionStatus.PUBLISHED) break;
            draft.checkCoreAssetReady();
            if ((draft.getPendingAssets() != null && !draft.getPendingAssets().isEmpty())
                    || (draft.getAssets() != null && draft.getAssets().stream().anyMatch(this::isAssetUnavailable))) {
                throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_NOT_READY);
            }
            // 仅在文件列表仍是已检查的快照时发布，防止同时申请上传后发布了旧正文。
            if (versionBundleBaseRepository.publishDraft(draft.getId(), draft.getRevision(), LocalDateTime.now()) != 0) break;
            if (attempt == MAX_DRAFT_UPDATE_ATTEMPTS - 1) {
                throw new OptimisticLockingFailureException("AI resource publication conflicted: " + resourceId);
            }
        }
        // 下一草稿存在后才切换主版本；任一步失败都可用同一已冻结版本重试。
        createDraftVersion(resourceId, draftVersion + 1);
        aiResourceBaseRepository.updateVersionByResourceId(resourceId, draftVersion, LocalDateTime.now());
    }

    private boolean isAssetUnavailable(AssetInfoBase asset) {
        return asset == null
                || !StringUtils.hasText(asset.getObjectKey())
                || asset.getUploadStatus() != AssetUploadStatus.AVAILABLE;
    }

    @Override
    public void handleFileUploaded(FileUploadedMessage msg) {
        if (msg.getScene() != getStorageScene()){
            return;
        }

        VT versionBundle = versionBundleBaseRepository.findFirstByAssetObjectKey(msg.getObjectKey()).orElse(null);
        if (versionBundle == null) {
            // 上传事件可能早于版本映射落库；不能把已成功上传的对象误判为孤儿并删除。
            // 发布前会通过 getUploadRecords 补偿状态，存储 GC 负责最终清理真正的孤儿对象。
            log.warn("AI resource asset upload callback arrived before version mapping. objectKey={}", msg.getObjectKey());
            return;
        }
        if (versionBundle.getStatus() != VersionStatus.DRAFT) return;
        updateDraftAssets(versionBundle.getResourceId(), versionBundle.getVersion(), draft -> {
            // 同时兼容升级前已经登记在 assets 中的上传。
            AssetInfoBase candidate = Stream.concat(draft.getPendingAssets().stream(), draft.getAssets().stream())
                    .filter(asset -> msg.getObjectKey().equals(asset.getObjectKey())).findFirst().orElse(null);
            if (candidate == null || candidate.getUploadStatus() == AssetUploadStatus.AVAILABLE) return;
            AssetInfoBase available = BeanUtil.copyProperties(candidate, AssetInfoBase.class);
            available.setSize(msg.getSize());
            available.setUploadStatus(AssetUploadStatus.AVAILABLE);
            draft.getPendingAssets().removeIf(asset -> msg.getObjectKey().equals(asset.getObjectKey()));
            draft.getAssets().removeIf(asset -> sameFile(asset, available));
            draft.getAssets().add(available);
        });
        // 取消或重试可能先于本次回调生效；只回收已经失去引用的候选对象。
        deleteUnreferencedObjectKeys(versionBundle.getResourceId(), Set.of(msg.getObjectKey()));
        log.info("AI resource asset upload handled. resourceId={} version={} objectKey={}",
                versionBundle.getResourceId(), versionBundle.getVersion(), msg.getObjectKey());
    }

    @Override
    @Transactional
    public void deleteAllVersionsByResourceIds(List<String> resourceIds) {
        Set<String> objectKeys = new HashSet<>();
        for (String resourceId : resourceIds) {
            List<VT> versionBundles = versionBundleBaseRepository.findByResourceId(resourceId);
            if (versionBundles.isEmpty()) continue;
            versionBundles.forEach(versionBundle -> objectKeys.addAll(objectKeys(versionBundle)));
        }
        versionBundleBaseRepository.deleteByResourceIdIn(resourceIds);
        eventPublisher.publishFileDeleteEvent(objectKeys.stream().filter(StringUtils::hasText).toList());
    }
}
