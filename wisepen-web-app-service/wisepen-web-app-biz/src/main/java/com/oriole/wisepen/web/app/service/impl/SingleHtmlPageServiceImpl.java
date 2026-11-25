package com.oriole.wisepen.web.app.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.web.app.config.WebAppProperties;
import com.oriole.wisepen.web.app.domain.base.SingleHtmlPageUploadMeta;
import com.oriole.wisepen.web.app.domain.base.SingleHtmlPageVersionBase;
import com.oriole.wisepen.web.app.domain.dto.req.SingleHtmlPageCreateRequest;
import com.oriole.wisepen.web.app.domain.dto.req.SingleHtmlPageUploadInitRequest;
import com.oriole.wisepen.web.app.domain.dto.res.SingleHtmlPageUploadInitResponse;
import com.oriole.wisepen.web.app.domain.dto.res.SingleHtmlPageVersionInfoResponse;
import com.oriole.wisepen.web.app.domain.entity.SingleHtmlPageEntity;
import com.oriole.wisepen.web.app.domain.entity.SingleHtmlPageVersionEntity;
import com.oriole.wisepen.web.app.enums.SingleHtmlPageStatus;
import com.oriole.wisepen.web.app.exception.WebAppError;
import com.oriole.wisepen.web.app.repository.SingleHtmlPageRepository;
import com.oriole.wisepen.web.app.repository.SingleHtmlPageVersionRepository;
import com.oriole.wisepen.web.app.service.ISingleHtmlPageService;
import com.oriole.wisepen.web.app.util.WisePenUiStyleInliner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class SingleHtmlPageServiceImpl implements ISingleHtmlPageService {

    private final SingleHtmlPageRepository singleHtmlPageRepository;
    private final SingleHtmlPageVersionRepository singleHtmlPageVersionRepository;
    private final RemoteResourceService remoteResourceService;
    private final RemoteStorageService remoteStorageService;
    private final WebAppProperties webAppProperties;
    private final WisePenUiStyleInliner wisePenUiStyleInliner;

    @Override
    public String createSingleHtmlPage(SingleHtmlPageCreateRequest request, Long ownerUserId,
                                       Map<Long, GroupRoleType> ownerGroupRoles) {
        String resourceId;
        try {
            resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                    .resourceName(request.getTitle())
                    .resourceType(ResourceType.SINGLE_HTML_PAGE)
                    .ownerId(ownerUserId.toString())
                    .ownerGroupRoles(ownerGroupRoles)
                    .mountTargetTagId(request.getMountTargetTagId())
                    .build()).getData();
        } catch (Exception e) {
            log.warn("single HTML page resource register failed. ownerUserId={} dependency=resourceService",
                    ownerUserId, e);
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_REGISTER_RESOURCE_FAILED, e.getMessage());
        }

        singleHtmlPageRepository.save(SingleHtmlPageEntity.builder()
                .resourceId(resourceId)
                .currentVersion(0)
                .build());
        log.info("single HTML page created. resourceId={} ownerUserId={} currentVersion={}",
                resourceId, ownerUserId, 0);
        return resourceId;
    }

    @Override
    public SingleHtmlPageUploadInitResponse initUploadSingleHtmlPage(
            SingleHtmlPageUploadInitRequest request, Long uploaderUserId, Map<Long, GroupRoleType> uploaderGroupRoles) {
        String extension = FileUtil.extName(request.getFilename()).toLowerCase();
        if (!"html".equals(extension) && !"htm".equals(extension)) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_FILE_TYPE_INVALID);
        }
        if (request.getExpectedSize() > webAppProperties.getMaxContentBytes()) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_CONTENT_TOO_LARGE);
        }

        SingleHtmlPageEntity singleHtmlPageEntity = null;
        if (request.getResourceId() != null) { // 如果 ResourceId 不为空，则说明不是首次上传
            singleHtmlPageEntity = getSingleHtmlPageInfo(request.getResourceId());
        }

        String singleHtmlPageId = IdUtil.fastSimpleUUID();
        UploadInitRespDTO upload;
        try {
            upload = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension("html")
                    .scene(StorageSceneEnum.PRIVATE_WEB_APP_ASSET)
                    .bizTag(singleHtmlPageId)
                    .expectedSize(request.getExpectedSize())
                    .isNeedCallback(true)
                    .build()).getData();
        } catch (Exception e) {
            log.warn("single HTML page upload init failed. singleHtmlPageId={} dependency=storageService",
                    singleHtmlPageId, e);
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_UPLOAD_INIT_FAILED, e.getMessage());
        }

        SingleHtmlPageUploadMeta meta = SingleHtmlPageUploadMeta.builder()
                .filename(request.getFilename())
                .uploaderId(uploaderUserId)
                .uploaderGroupRoles(uploaderGroupRoles)
                .expectedSize(request.getExpectedSize())
                .expectedMd5(request.getMd5())
                .mountTargetTagId(request.getMountTargetTagId())
                .build();

        SingleHtmlPageVersionEntity versionEntity = SingleHtmlPageVersionEntity.builder()
                .singleHtmlPageId(singleHtmlPageId)
                .resourceId(request.getResourceId())
                .version(1)
                .singleHtmlPageStatus(Boolean.TRUE.equals(upload.getFlashUploaded())
                        ? SingleHtmlPageStatus.UPLOADED : SingleHtmlPageStatus.UPLOADING)
                .uploadMeta(meta)
                .objectKey(upload.getObjectKey())
                .build();

        if (singleHtmlPageEntity != null) { // 如果不是第一次上传文档
            Integer nextVersion = singleHtmlPageEntity.getCurrentVersion() + 1;
            // 检查版本冲突（未成功的版本）
            singleHtmlPageVersionRepository.findByResourceIdAndVersion(singleHtmlPageEntity.getResourceId(), nextVersion)
                    .ifPresent(existing -> {
                        if (existing.getSingleHtmlPageStatus() != null && existing.getSingleHtmlPageStatus() == SingleHtmlPageStatus.READY) {
                            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_CONFLICT);
                        }
                        remoteStorageService.deleteFiles(List.of(upload.getObjectKey()));
                    });

            versionEntity.setVersion(nextVersion);
            versionEntity.setResourceId(singleHtmlPageEntity.getResourceId());
            // 非首次上传时 MountTargetTagId 字段无效
            versionEntity.getUploadMeta().setMountTargetTagId(null);
        }

        singleHtmlPageVersionRepository.save(versionEntity);


        log.info("single HTML page upload initialized. singleHtmlPageId={} objectKey={} flashUploaded={}",
                singleHtmlPageId, upload.getObjectKey(), upload.getFlashUploaded());

        SingleHtmlPageUploadInitResponse resp = BeanUtil.copyProperties(upload, SingleHtmlPageUploadInitResponse.class);
        resp.setSingleHtmlPageId(singleHtmlPageId);
        resp.setVersion(versionEntity.getVersion());

        if (Boolean.TRUE.equals(upload.getFlashUploaded())) {
            // 如果触发秒传
            finalizeToReady(singleHtmlPageId);
        }
        return resp;
    }

    @Override
    public void finalizeToReady(String singleHtmlPageId) {
        SingleHtmlPageVersionEntity versionEntity = singleHtmlPageVersionRepository.findById(singleHtmlPageId)
                .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND));

        if (versionEntity.getSingleHtmlPageStatus() != null
                && versionEntity.getSingleHtmlPageStatus() == SingleHtmlPageStatus.READY) {
            return;
        }

        singleHtmlPageVersionRepository.updateStatusById(singleHtmlPageId, SingleHtmlPageStatus.REGISTERING_RES);

        SingleHtmlPageEntity infoEntity;
        String resourceId = versionEntity.getResourceId();
        if (resourceId != null) { // 非首次上传 SingleHtmlPage
            infoEntity = singleHtmlPageRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_NOT_FOUND));
            // 更新 SingleHtmlPageEntity 的版本号
            singleHtmlPageRepository.updateVersionByResourceId(
                    infoEntity.getResourceId(), infoEntity.getCurrentVersion() + 1, LocalDateTime.now());
        } else { // 首次上传 SingleHtmlPage
            // 向 resource 服务注册资源
            try {
                resourceId = remoteResourceService.createResource(
                        ResourceCreateReqDTO.builder()
                                .resourceName(versionEntity.getUploadMeta().getFilename())
                                .resourceType(ResourceType.SINGLE_HTML_PAGE)
                                .ownerId(versionEntity.getUploadMeta().getUploaderId().toString())
                                .ownerGroupRoles(versionEntity.getUploadMeta().getUploaderGroupRoles())
                                .size(versionEntity.getUploadMeta().getExpectedSize())
                                .mountTargetTagId(versionEntity.getUploadMeta().getMountTargetTagId())
                                .build()
                ).getData();
            } catch (Exception e) {
                log.error("single HTML page resource register failed. singleHtmlPageId={}", singleHtmlPageId, e);
                singleHtmlPageVersionRepository.updateStatusById(
                        singleHtmlPageId, SingleHtmlPageStatus.REGISTERING_RES_TIMEOUT);
                throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_REGISTER_RESOURCE_FAILED, e.getMessage());
            }
            // 新建 SingleHtmlPageEntity
            infoEntity = SingleHtmlPageEntity.builder().resourceId(resourceId).currentVersion(1).build();
            singleHtmlPageRepository.save(infoEntity);
            // 更新版本记录的 ResourceId
            singleHtmlPageVersionRepository.updateResourceIdById(singleHtmlPageId, resourceId);
        }

        singleHtmlPageVersionRepository.updateStatusById(singleHtmlPageId, SingleHtmlPageStatus.READY);
        log.debug("single HTML page ready finalized. singleHtmlPageId={} resourceId={}", singleHtmlPageId, resourceId);
    }

    @Override
    public void assertVersionUploader(String singleHtmlPageId, Long uploaderUserId) {
        SingleHtmlPageVersionEntity version = singleHtmlPageVersionRepository.findById(singleHtmlPageId)
                .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND));
        if (!Objects.equals(version.getUploadMeta().getUploaderId(), uploaderUserId)) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED);
        }
    }

    @Override
    public SingleHtmlPageStatus refreshSingleHtmlPageStatus(String singleHtmlPageId) {
        SingleHtmlPageVersionEntity version = singleHtmlPageVersionRepository.findById(singleHtmlPageId)
                .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND));
        if (version.getSingleHtmlPageStatus() == SingleHtmlPageStatus.READY || version.getSingleHtmlPageStatus() == SingleHtmlPageStatus.FAILED) {
            return version.getSingleHtmlPageStatus();
        }

        StorageRecordDTO storageRecord = remoteStorageService.getFileRecord(version.getObjectKey()).getData();
        if (storageRecord == null) {
            return version.getSingleHtmlPageStatus();
        }
        version.setSize(storageRecord.getSize());
        version.setSingleHtmlPageStatus(SingleHtmlPageStatus.UPLOADED);
        singleHtmlPageVersionRepository.save(version);
        finalizeToReady(singleHtmlPageId);
        return SingleHtmlPageStatus.READY;
    }

    @Override
    public SingleHtmlPageEntity getSingleHtmlPageInfo(String resourceId) {
        return singleHtmlPageRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_NOT_FOUND));
    }

    @Override
    public SingleHtmlPageVersionEntity getSingleHtmlPageVersion(String resourceId, Integer targetVersion) {
        if (targetVersion == null) {
            SingleHtmlPageVersionEntity resource = singleHtmlPageVersionRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND));
            targetVersion = resource.getVersion();
        }
        return singleHtmlPageVersionRepository.findByResourceIdAndVersion(resourceId, targetVersion)
                .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND));
    }

    @Override
    public String getSingleHtmlPageContent(String resourceId, Integer version) {
        SingleHtmlPageVersionEntity versionEntity = getSingleHtmlPageVersion(resourceId, version);
        byte[] content;
        try {
            content = remoteStorageService.getSmallFileContent(versionEntity.getObjectKey()).getData();
        } catch (Exception e) {
            log.warn("single HTML page content read failed. resourceId={} version={} objectKey={} dependency=storageService",
                    resourceId, versionEntity.getVersion(), versionEntity.getObjectKey(), e);
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_CONTENT_READ_FAILED, e.getMessage());
        }
        if (content == null) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_CONTENT_READ_FAILED);
        }
        return wisePenUiStyleInliner.inline(new String(content, StandardCharsets.UTF_8));
    }

    @Override
    public PageR<SingleHtmlPageVersionInfoResponse> listVersions(String resourceId, int page, int size) {
        getSingleHtmlPageInfo(resourceId);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<SingleHtmlPageVersionEntity> entityPage = singleHtmlPageVersionRepository
                .findByResourceIdAndSingleHtmlPageStatusOrderByVersionDesc(resourceId, SingleHtmlPageStatus.READY, pageable);
        PageR<SingleHtmlPageVersionInfoResponse> response = new PageR<>(entityPage.getTotalElements(), page, size);
        response.addAll(entityPage.getContent().stream()
                .map(entity -> BeanUtil.copyProperties(entity, SingleHtmlPageVersionInfoResponse.class))
                .toList());
        return response;
    }

    @Override
    public List<SingleHtmlPageVersionBase> listPending(Long uploaderId) {
        List<SingleHtmlPageVersionEntity> entities = singleHtmlPageVersionRepository.findByUploadMetaUploaderIdAndSingleHtmlPageStatusInOrderByCreateTimeDesc(
                uploaderId,
                List.of(SingleHtmlPageStatus.UPLOADING,
                        SingleHtmlPageStatus.UPLOADED,
                        SingleHtmlPageStatus.FAILED
                )
        );
        return new ArrayList<>(entities);
    }

    @Override
    public void deletedSingleHtmlPageVersion(String singleHtmlPageId) {
        SingleHtmlPageVersionEntity version = singleHtmlPageVersionRepository.findById(singleHtmlPageId)
                .orElseThrow(() -> new ServiceException(WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND));
        if (version.getSingleHtmlPageStatus() == SingleHtmlPageStatus.READY) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_PROCESS_STATE_INVALID);
        }
        remoteStorageService.deleteFiles(List.of(version.getObjectKey()));
        singleHtmlPageVersionRepository.deleteById(singleHtmlPageId);
        log.info("single HTML page process aborted. singleHtmlPageId={}", singleHtmlPageId);
    }

    @Override
    public void deletePages(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        List<String> objectKeys = singleHtmlPageVersionRepository.findByResourceIdIn(resourceIds).stream()
                .map(SingleHtmlPageVersionEntity::getObjectKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!objectKeys.isEmpty()) {
            remoteStorageService.deleteFiles(objectKeys);
        }
        singleHtmlPageVersionRepository.deleteByResourceIdIn(resourceIds);
        singleHtmlPageRepository.deleteByResourceIdIn(resourceIds);
        log.info("single HTML pages deleted. count={} resourceIds={}", resourceIds.size(), summarizeIds(resourceIds));
    }
}
