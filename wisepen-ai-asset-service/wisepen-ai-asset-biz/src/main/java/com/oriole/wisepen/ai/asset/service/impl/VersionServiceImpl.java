package com.oriole.wisepen.ai.asset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.entity.BaseVersionBundleEntity;
import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * skill / agent 共用的版本生命周期实现，差异通过 VersionServiceProfile 注入，不加 @Service 由配置类装配两份
 */
@Slf4j
@RequiredArgsConstructor
public class VersionServiceImpl<T extends BaseVersionBundleEntity> implements IVersionService<T> {

    private static final String ROOT_PATH = "/";

    private final VersionServiceProfile<T> profile;
    private final RemoteStorageService remoteStorageService;
    private final AIAssetEventPublisher eventPublisher;

    @Override
    public void createDraft(String resourceId, Integer draftVersion) {
        T draft = profile.getDraftFactory().get();
        draft.setResourceId(resourceId);
        draft.setVersion(draftVersion);
        draft.setStatus(VersionStatus.DRAFT);
        draft.setAssets(new ArrayList<>());
        // 如果不是首份草案(1)需要复制此前的资源列表与运行配置
        if (draftVersion > 1) {
            profile.getRepository().findByResourceIdAndVersion(resourceId, draftVersion - 1).ifPresent(current -> {
                draft.setAssets(current.getAssets());
                draft.setSpec(current.getSpec());
            });
        }
        profile.getRepository().save(draft);
    }

    @Override
    public T getBundle(String resourceId, Integer version) {
        // 如果未指定版本号则使用当前发布的版本号
        if (version == null) version = profile.getPublishedVersionLoader().apply(resourceId);
        return profile.getRepository().findByResourceIdAndVersion(resourceId, version)
                .orElseThrow(() -> new ServiceException(profile.getVersionNotFound()));
    }

    @Override
    @Transactional
    public AssetUploadInitResponse initUploadAssets(AssetUploadInitRequest req) {
        // 检查当前是否是草案版本
        T draft = profile.getRepository().findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(profile.getVersionNotFound()));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(profile.getNonDraft());

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
                        .scene(profile.getScene())
                        .bizTag(req.getResourceId())
                        .expectedSize(assetReq.getExpectedSize())
                        .build()).getData();
            } catch (Exception e) {
                log.warn("{} asset upload init failed. resourceId={} version={} dependency=storageService",
                        profile.getLogTag(), req.getResourceId(), req.getDraftVersion(), e);
                throw new ServiceException(profile.getUploadApplyFailed(), e.getMessage());
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

        profile.getRepository().save(draft);
        deleteUnreferencedObjectKeys(req.getResourceId(), replacedObjectKeys);
        return response;
    }

    private void validateDirectoryPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/") || path.contains("\\")
                || path.contains("//") || path.contains("/../") || path.endsWith("/..")
                || (!ROOT_PATH.equals(path) && path.endsWith("/"))) {
            throw new ServiceException(profile.getPathInvalid());
        }
    }

    private void validateFileName(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new ServiceException(profile.getPathInvalid());
        }
    }

    private AssetInfoBase findOrCreateDraftAsset(T draft, String path, String name, AssetResourceType assetResourceType) {
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
        T draft = profile.getRepository().findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(profile.getVersionNotFound()));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(profile.getNonDraft());

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

        profile.getRepository().save(draft);
        deleteUnreferencedObjectKeys(req.getResourceId(), removedObjectKeys);
    }

    private void deleteUnreferencedObjectKeys(String resourceId, Set<String> objectKeys) {
        if (objectKeys.isEmpty()) return;

        Set<String> referencedObjectKeys = new HashSet<>();
        profile.getRepository().findByResourceId(resourceId).forEach(version -> {
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
        int draftVersion = profile.getPublishedVersionLoader().apply(resourceId) + 1;
        // 检查当否是草案版本
        T draft = profile.getRepository().findByResourceIdAndVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(profile.getVersionNotFound()));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(profile.getNonDraft());
        // 类型相关的核心校验（skill 核心文件 / agent spec）下沉到实体
        draft.checkReadyToPublish();
        // 资源未就绪（有上传中的资源）
        if (draft.getAssets() != null && draft.getAssets().stream().anyMatch(this::isAssetUnavailable)) {
            throw new ServiceException(profile.getAssetNotReady());
        }
        draft.setStatus(VersionStatus.PUBLISHED);
        profile.getPublishedVersionUpdater().accept(resourceId, draftVersion);
        profile.getRepository().save(draft);

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
        T versionBundle = profile.getRepository().findFirstByAssetObjectKey(msg.getObjectKey()).orElse(null);
        if (versionBundle == null) {
            // 未找到对应的版本，删除文件
            eventPublisher.publishFileDeleteEvent(List.of(msg.getObjectKey()));
            log.warn("{} asset upload compensated for missing version. objectKey={}", profile.getLogTag(), msg.getObjectKey());
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
        profile.getRepository().save(versionBundle);

        log.info("{} asset upload handled. resourceId={} version={} assetId={} objectKey={}",
                profile.getLogTag(), versionBundle.getResourceId(), versionBundle.getVersion(), asset.getId(), msg.getObjectKey());
    }

    @Override
    @Transactional
    public void deleteAllVersionsByResourceIds(List<String> resourceIds) {
        Set<String> objectKeys = new HashSet<>();
        for (String resourceId : resourceIds) {
            List<T> versionBundles = profile.getRepository().findByResourceId(resourceId);
            if (versionBundles.isEmpty()) continue;
            versionBundles.forEach(versionBundle -> {
                if (versionBundle.getAssets() != null) versionBundle.getAssets().forEach(asset ->
                        objectKeys.add(asset.getObjectKey()));
            });
        }
        profile.getRepository().deleteByResourceIdIn(resourceIds);
        eventPublisher.publishFileDeleteEvent(objectKeys.stream().toList());
    }
}
