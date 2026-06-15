package com.oriole.wisepen.ai.asset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.entity.AIResourceBaseEntity;
import com.oriole.wisepen.ai.asset.domain.entity.VersionBundleBaseEntity;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import com.oriole.wisepen.ai.asset.exception.AIResourceError;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.repository.AIResourceBaseRepository;
import com.oriole.wisepen.ai.asset.repository.VersionBundleBaseRepository;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * skill / agent 共用的版本生命周期模板，类型差异由子类经 buildDraft / getStorageScene 提供
 */
@Slf4j
@RequiredArgsConstructor
public abstract class VersionServiceImpl<VT extends VersionBundleBaseEntity, AT extends AIResourceBaseEntity>
        implements IVersionService<VT, AT> {

    private static final String ROOT_PATH = "/";

    VersionBundleBaseRepository<VT> versionBundleBaseRepository;
    AIResourceBaseRepository<AT> aiResourceBaseRepository;

    private final RemoteStorageService remoteStorageService;
    private final AIAssetEventPublisher eventPublisher;

    // 新建一个空草稿版本包
    protected abstract VT buildDraft(String resourceId, Integer draftVersion);

    // 该类型资产使用的对象存储场景
    protected abstract StorageSceneEnum getStorageScene();

    @Override
    public void createDraft(String resourceId, Integer draftVersion) {
        VT draft = buildDraft(resourceId, draftVersion);
        // 如果不是首份草案(1)需要复制此前的资源列表与运行配置
        if (draftVersion > 1) {
            versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, draftVersion - 1).ifPresent(draft::copyBy);
        }
        versionBundleBaseRepository.save(draft);
    }

    @Override
    public VT getBundle(String resourceId, Integer version) {
        // 如果未指定版本号则使用当前发布的版本号
        if (version == null) {
            AT resource = aiResourceBaseRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_NOT_FOUND));
            version = resource.getVersion();
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
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_VERSION);

        Set<String> replacedObjectKeys = new HashSet<>();

        AssetUploadInitResponse response = AssetUploadInitResponse.builder()
                .resourceId(req.getResourceId())
                .version(draft.getVersion())
                .build();

        for (AssetUploadInitRequest.AssetUploadRequest assetReq : req.getAssets()) {
            // 校验 Path 和 Name 合法性
            validateDirectoryPath(assetReq.getPath());
            validateFileName(assetReq.getName());
            // 找到旧 Asset 为覆盖，未找到则为新增 Asset
            AssetInfoBase asset = findOrCreateDraftAsset(draft, assetReq.getPath(), assetReq.getName(), assetReq.getAssetResourceType());
            String oldObjectKey = asset.getObjectKey();

            UploadInitRespDTO uploadInitRespDTO;
            try {
                uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                        .md5(assetReq.getMd5())
                        .extension(assetReq.getAssetResourceType().getExtension())
                        .scene(getStorageScene())
                        .bizTag(req.getResourceId())
                        .expectedSize(assetReq.getExpectedSize())
                        .build()).getData();
            } catch (Exception e) {
                log.warn("{} asset upload init failed. resourceId={} version={} dependency=storageService",
                        getStorageScene(), req.getResourceId(), req.getDraftVersion(), e);
                throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_UPLOAD_URL_APPLY_FAILED, e.getMessage());
            }

            asset.setObjectKey(uploadInitRespDTO.getObjectKey());
            asset.setSize(assetReq.getExpectedSize());
            asset.setUploadStatus(Boolean.TRUE.equals(uploadInitRespDTO.getFlashUploaded()) ? AssetUploadStatus.AVAILABLE : AssetUploadStatus.UPLOADING);

            AssetUploadInitResponse.AssetUploadTicket assetUploadTicket = AssetUploadInitResponse.AssetUploadTicket
                    .builder().assetId(asset.getId())
                    .path(asset.getPath())
                    .name(asset.getName()).build();
            BeanUtil.copyProperties(uploadInitRespDTO, assetUploadTicket);
            response.getAssetUploadTickets().add(assetUploadTicket);

            // 只要oldObjectKey有值，说明是替代，则检查oldObjectKey是否可移除
            if (StringUtils.hasText(oldObjectKey)) replacedObjectKeys.add(oldObjectKey);
        }

        versionBundleBaseRepository.save(draft);
        deleteUnreferencedObjectKeys(req.getResourceId(), replacedObjectKeys);
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

    private AssetInfoBase findOrCreateDraftAsset(VT draft, String path, String name, AssetResourceType assetResourceType) {
        if (draft.getAssets() == null) draft.setAssets(new ArrayList<>());
        AssetInfoBase asset = draft.getAssets().stream()
                .filter(item -> path.equals(item.getPath()) && name.equals(item.getName()))
                .findFirst().orElse(null);
        if (asset == null) {
            asset = AssetInfoBase.builder().id(IdUtil.fastSimpleUUID())
                    .path(path).name(name).skillAssetResourceType(assetResourceType)
                    .uploadStatus(AssetUploadStatus.UPLOADING)
                    .build();
            draft.getAssets().add(asset);
        }
        return asset;
    }

    @Override
    public void deleteAssets(AssetDeleteRequest req) {
        // 检查是否是草案版本
        VT draft = versionBundleBaseRepository.findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_VERSION);

        Set<String> removedObjectKeys = new HashSet<>();

        for (String assetId : req.getAssetIds()) {
            AssetInfoBase removed = draft.getAssets().stream()
                    .filter(asset -> assetId.equals(asset.getId()))
                    .findFirst()
                    .orElse(null);
            if (removed != null) {
                draft.getAssets().remove(removed);
                removedObjectKeys.add(removed.getObjectKey());
            }
        }

        versionBundleBaseRepository.save(draft);
        deleteUnreferencedObjectKeys(req.getResourceId(), removedObjectKeys);
    }

    private void deleteUnreferencedObjectKeys(String resourceId, Set<String> objectKeys) {
        if (objectKeys.isEmpty()) return;

        Set<String> referencedObjectKeys = new HashSet<>();
        versionBundleBaseRepository.findByResourceId(resourceId).forEach(version -> {
            if (version.getAssets() != null) version.getAssets().forEach(asset ->
                    referencedObjectKeys.add(asset.getObjectKey()));
        });

        List<String> deletableObjectKeys = objectKeys.stream()
                .filter(objectKey -> !referencedObjectKeys.contains(objectKey))
                .toList();

        eventPublisher.publishFileDeleteEvent(deletableObjectKeys);
    }

    @Override
    @Transactional
    public void publishVersion(String resourceId) {
        AT resource = aiResourceBaseRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_NOT_FOUND));
        int draftVersion = resource.getVersion() + 1;
        // 检查当否是草案版本
        VT draft = versionBundleBaseRepository.findByResourceIdAndVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_VERSION);
        // 类型相关的核心校验（skill 核心文件 / agent spec）下沉到实体
        draft.checkReadyToPublish();
        // 资源未就绪（有上传中的资源）
        if (draft.getAssets() != null && draft.getAssets().stream().anyMatch(this::isAssetUnavailable)) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_ASSET_NOT_READY);
        }
        draft.setStatus(VersionStatus.PUBLISHED);
        aiResourceBaseRepository.updateVersionByResourceId(resourceId, draftVersion);
        versionBundleBaseRepository.save(draft);

        // 新草案是 version + 1，直接新建
        createDraft(resourceId, draftVersion + 1);
    }

    private boolean isAssetUnavailable(AssetInfoBase asset) {
        return asset == null
                || !StringUtils.hasText(asset.getObjectKey())
                || asset.getUploadStatus() != AssetUploadStatus.AVAILABLE;
    }

    @Override
    public void handleFileUploaded(FileUploadedMessage msg) {
        VT versionBundle = versionBundleBaseRepository.findFirstByAssetObjectKey(msg.getObjectKey()).orElse(null);
        if (versionBundle == null) {
            // 未找到对应的版本，删除文件
            eventPublisher.publishFileDeleteEvent(List.of(msg.getObjectKey()));
            log.warn("{} asset upload compensated for missing version. objectKey={}", getStorageScene(), msg.getObjectKey());
            return;
        }
        // 能按 objectKey 查到版本，资产列表中必然有对应项
        AssetInfoBase asset = versionBundle.getAssets().stream()
                .filter(item -> msg.getObjectKey().equals(item.getObjectKey()))
                .findFirst()
                .orElse(null);
        assert asset != null;
        if (asset.getUploadStatus() == AssetUploadStatus.AVAILABLE) return;

        asset.setSize(msg.getSize());
        asset.setUploadStatus(AssetUploadStatus.AVAILABLE);
        versionBundleBaseRepository.save(versionBundle);

        log.info("{} asset upload handled. resourceId={} version={} assetId={} objectKey={}",
                getStorageScene(), versionBundle.getResourceId(), versionBundle.getVersion(), asset.getId(), msg.getObjectKey());
    }

    @Override
    @Transactional
    public void deleteAllVersionsByResourceIds(List<String> resourceIds) {
        Set<String> objectKeys = new HashSet<>();
        for (String resourceId : resourceIds) {
            List<VT> versionBundles = versionBundleBaseRepository.findByResourceId(resourceId);
            if (versionBundles.isEmpty()) continue;
            versionBundles.forEach(versionBundle -> {
                if (versionBundle.getAssets() != null) versionBundle.getAssets().forEach(asset ->
                        objectKeys.add(asset.getObjectKey()));
            });
        }
        versionBundleBaseRepository.deleteByResourceIdIn(resourceIds);
        eventPublisher.publishFileDeleteEvent(objectKeys.stream().toList());
    }
}
