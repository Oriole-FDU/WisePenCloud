package com.oriole.wisepen.ai.asset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillVersionPublishRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.domain.entity.SkillEntity;
import com.oriole.wisepen.ai.asset.domain.entity.SkillVersionBundleEntity;
import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.enums.AssetResourceType;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import com.oriole.wisepen.ai.asset.exception.SkillError;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.repository.SkillRepository;
import com.oriole.wisepen.ai.asset.repository.SkillVersionBundleRepository;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillVersionService implements IVersionService {

    private static final String ROOT_PATH = "/";
    private static final String MAIN_SKILL_MD = "SKILL.md";

    private final SkillRepository skillRepository;
    private final SkillVersionBundleRepository skillVersionBundleRepository;
    private final RemoteStorageService remoteStorageService;
    private final AIAssetEventPublisher eventPublisher;

    @Override
    public void createDraftSkillVersion(String resourceId, Integer draftVersion) {
        SkillVersionBundleEntity draft = SkillVersionBundleEntity.builder()
                .resourceId(resourceId)
                .version(draftVersion)
                .status(VersionStatus.DRAFT)
                .skillAssets(new ArrayList<>())
                .build();
        // 如果不是首份草案(1)需要复制此前的资源列表
        if (draftVersion > 1) {
            skillVersionBundleRepository.findByResourceIdAndVersion(resourceId, draftVersion - 1).ifPresent(current -> {
                draft.setMainSkillMD(current.getMainSkillMD());
                draft.setSkillAssets(current.getSkillAssets());
            });
        }
        skillVersionBundleRepository.save(draft);
    }

    @Override
    public SkillVersionBundleInfoResponse getSkillVersionBundle(String resourceId, Integer version) {
        // 如果未指定版本号则使用当前发布的版本号
        if (version == null){
            SkillEntity skill = skillRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(SkillError.SKILL_NOT_FOUND));
            version = skill.getVersion();
        }
        SkillVersionBundleEntity entity = skillVersionBundleRepository.findByResourceIdAndVersion(resourceId, version)
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_VERSION_NOT_FOUND));
        return BeanUtil.copyProperties(entity, SkillVersionBundleInfoResponse.class);
    }

    @Override
    @Transactional
    public AssetUploadInitResponse initUploadSkillAssets(AssetUploadInitRequest req) {
        // 检查当前是否是草案版本
        SkillVersionBundleEntity draft = skillVersionBundleRepository.findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION);

        Set<String> replacedObjectKeys = new HashSet<>();

        AssetUploadInitResponse response = AssetUploadInitResponse.builder()
                .resourceId(req.getResourceId())
                .version(draft.getVersion())
                .build();

        for (AssetUploadInitRequest.AssetUploadRequest assetReq : req.getAssets()) {
            // 校验 Path 和 Name 合法性
            validateDirectoryPath(assetReq.getPath());
            validateFileName(assetReq.getName());
            // 查找或新增目标 Asset
            // 找到旧 Asset 为覆盖，未找到则为新增 Asset
            AssetInfoBase asset = findOrCreateDraftAsset(draft, assetReq.getPath(), assetReq.getName(), assetReq.getSkillAssetResourceType());
            String oldObjectKey = asset.getObjectKey();

            UploadInitRespDTO uploadInitRespDTO;
            try {
                uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                        .md5(assetReq.getMd5())
                        .extension(assetReq.getSkillAssetResourceType().getExtension())
                        .scene(StorageSceneEnum.PRIVATE_SKILL_ASSET)
                        .bizTag(req.getResourceId())
                        .expectedSize(assetReq.getExpectedSize())
                        .build()).getData();
            }
            catch (Exception e) {
                log.warn("skill asset upload init failed. resourceId={} version={} dependency=storageService",
                        req.getResourceId(), req.getDraftVersion(), e);
                throw new ServiceException(SkillError.SKILL_ASSET_UPLOAD_URL_APPLY_FAILED, e.getMessage());
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

        skillVersionBundleRepository.save(draft);
        deleteUnreferencedObjectKeys(req.getResourceId(), replacedObjectKeys);
        return response;
    }

    private void validateDirectoryPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/") || path.contains("\\")
                || path.contains("//") || path.contains("/../") || path.endsWith("/..")
                || (!ROOT_PATH.equals(path) && path.endsWith("/"))) {
            throw new ServiceException(SkillError.SKILL_ASSET_PATH_INVALID);
        }
    }

    private void validateFileName(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || ".".equals(name) || "..".equals(name)) {
            throw new ServiceException(SkillError.SKILL_ASSET_PATH_INVALID);
        }
    }

    private AssetInfoBase findOrCreateDraftAsset(SkillVersionBundleEntity draft, String path, String name, AssetResourceType skillAssetResourceType) {
        if (draft.getSkillAssets() == null) draft.setSkillAssets(new ArrayList<>());
        AssetInfoBase asset;
        // 主 Asset
        if (ROOT_PATH.equals(path) && MAIN_SKILL_MD.equals(name)) {
            asset = draft.getMainSkillMD();
        } else {
            // 非主 Asset
            asset = draft.getSkillAssets().stream()
                    .filter(item-> path.equals(item.getPath()) && name.equals(item.getName()))
                    .findFirst().orElse(null);
        }
        if (asset == null) {
            asset = AssetInfoBase.builder().id(IdUtil.fastSimpleUUID())
                    .path(path).name(name).skillAssetResourceType(skillAssetResourceType)
                    .uploadStatus(AssetUploadStatus.UPLOADING)
                    .build();
            // 主 Asset setMainSkillMD，非主 Asset 加入draft.getSkillAssets()
            if (ROOT_PATH.equals(path) && MAIN_SKILL_MD.equals(name)) draft.setMainSkillMD(asset);
            else draft.getSkillAssets().add(asset);
        }
        return asset;
    }

    @Override
    public void deleteSkillAssets(AssetDeleteRequest req) {
        // 检查是否是草案版本
        SkillVersionBundleEntity draft = skillVersionBundleRepository.findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION);

        Set<String> removedObjectKeys = new HashSet<>();

        for (String assetId : req.getAssetIds()) {
            if (draft.getMainSkillMD() != null && assetId.equals(draft.getMainSkillMD().getId())) {
                removedObjectKeys.add(draft.getMainSkillMD().getObjectKey());
                draft.setMainSkillMD(null);
            } else {
                AssetInfoBase removed = draft.getSkillAssets().stream()
                        .filter(asset -> assetId.equals(asset.getId()))
                        .findFirst()
                        .orElse(null);
                if (removed != null){
                    draft.getSkillAssets().remove(removed);
                    removedObjectKeys.add(removed.getObjectKey());
                }
            }
        }

        skillVersionBundleRepository.save(draft);
        deleteUnreferencedObjectKeys(req.getResourceId(), removedObjectKeys);
    }

    private void deleteUnreferencedObjectKeys(String resourceId, Set<String> objectKeys) {
        if (objectKeys.isEmpty()) return;

        Set<String> referencedObjectKeys = new HashSet<>();
        skillVersionBundleRepository.findByResourceId(resourceId).forEach(version ->{
            if (version.getMainSkillMD() != null) referencedObjectKeys.add(version.getMainSkillMD().getObjectKey());
            if (version.getSkillAssets() != null) version.getSkillAssets().forEach(asset ->
                    referencedObjectKeys.add(asset.getObjectKey())
            );
        });

        List<String> deletableObjectKeys = objectKeys.stream()
                .filter(objectKey -> !referencedObjectKeys.contains(objectKey))
                .toList();

        eventPublisher.publishFileDeleteEvent(deletableObjectKeys);
    }

    @Override
    @Transactional
    public void publishSkillVersion(SkillVersionPublishRequest req) {
        SkillEntity skill = skillRepository.findByResourceId(req.getResourceId())
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_NOT_FOUND));

        int draftVersion = skill.getVersion() + 1;
        // 检查当否是草案版本
        SkillVersionBundleEntity draft = skillVersionBundleRepository.findByResourceIdAndVersion(req.getResourceId(), draftVersion)
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION);
        // 核心资源缺失
        if (isSkillDraftUnavailable(draft.getMainSkillMD())) {
            throw new ServiceException(SkillError.SKILL_CORE_ASSET_NOT_FOUND);
        }
        // 资源未就绪（有上传中的资源）
        if (draft.getSkillAssets() != null && draft.getSkillAssets().stream().anyMatch(this::isSkillDraftUnavailable)) {
            throw new ServiceException(SkillError.SKILL_ASSET_NOT_READY);
        }
        draft.setStatus(VersionStatus.PUBLISHED);
        skillRepository.updateVersionByResourceId(req.getResourceId(), draftVersion);
        skillVersionBundleRepository.save(draft);

        // 新草案是 version + 1，直接新建
        createDraftSkillVersion(req.getResourceId(), draftVersion + 1);
    }

    private boolean isSkillDraftUnavailable(AssetInfoBase asset) {
        return asset == null
                || !StringUtils.hasText(asset.getObjectKey())
                || asset.getUploadStatus() != AssetUploadStatus.AVAILABLE;
    }

    @Override
    public void handleFileUploaded(FileUploadedMessage msg) {
        if (msg.getScene() != StorageSceneEnum.PRIVATE_SKILL_ASSET){
            return; // 不处理非PRIVATE_SKILL_ASSET的上传通知
        }

        SkillVersionBundleEntity versionBundle = skillVersionBundleRepository.findFirstByAssetObjectKey(msg.getObjectKey()).orElse(null);
        if (versionBundle == null) {
            // 未找到对应的版本，删除文件
            eventPublisher.publishFileDeleteEvent(List.of(msg.getObjectKey()));
            log.warn("skill asset upload compensated for missing version. objectKey={}", msg.getObjectKey());
            return;
        }
        AssetInfoBase asset = null;
        if (versionBundle.getMainSkillMD() != null && msg.getObjectKey().equals(versionBundle.getMainSkillMD().getObjectKey())) {
            asset = versionBundle.getMainSkillMD();
        } else { // version.getSkillAssets() 不可能为空，否则不可能找到 Skill 的版本
            asset = versionBundle.getSkillAssets().stream()
                    .filter(item -> msg.getObjectKey().equals(item.getObjectKey()))
                    .findFirst()
                    .orElse(null);
        }
        assert asset != null; // 断言 asset 不为空，因为否则不可能找到 Skill 的版本
        if (asset.getUploadStatus() == AssetUploadStatus.AVAILABLE) {
            return;
        }

        asset.setSize(msg.getSize());
        asset.setUploadStatus(AssetUploadStatus.AVAILABLE);
        skillVersionBundleRepository.save(versionBundle);

        log.info("skill asset upload handled. resourceId={} version={} assetId={} objectKey={}",
                versionBundle.getResourceId(), versionBundle.getVersion(), asset.getId(), msg.getObjectKey());
    }

    @Override
    @Transactional
    public void deleteAllVersionsByResourceIds(List<String> resourceIds) {
        Set<String> objectKeys = new HashSet<>();
        for (String resourceId : resourceIds) {
            List<SkillVersionBundleEntity> versionBundles = skillVersionBundleRepository.findByResourceId(resourceId);
            if(versionBundles.isEmpty()) continue;
            versionBundles.forEach(versionBundle ->{
                if (versionBundle.getMainSkillMD() != null) objectKeys.add(versionBundle.getMainSkillMD().getObjectKey());
                if (versionBundle.getSkillAssets() != null) versionBundle.getSkillAssets().forEach(asset ->
                    objectKeys.add(asset.getObjectKey())
                );
            });
        }
        skillVersionBundleRepository.deleteByResourceIdIn(resourceIds);
        eventPublisher.publishFileDeleteEvent(objectKeys.stream().toList());
    }
}
