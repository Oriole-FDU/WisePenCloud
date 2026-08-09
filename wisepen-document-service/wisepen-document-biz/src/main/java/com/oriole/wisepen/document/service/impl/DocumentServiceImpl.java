package com.oriole.wisepen.document.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.constant.DocumentConstants;
import com.oriole.wisepen.document.api.domain.base.DocumentVersionBase;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.base.DocumentUploadMeta;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentCreateRequest;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentForkRequest;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentVersionInfoResponse;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.repository.DocumentContentRepository;
import com.oriole.wisepen.document.repository.DocumentPdfMetaRepository;
import com.oriole.wisepen.document.repository.DocumentInfoRepository;
import com.oriole.wisepen.document.repository.DocumentVersionRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.document.service.IOnlyOfficeEditService;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.document.exception.DocumentError.DOCUMENT_HAS_NO_VERSION;

/**
 * 文档上传与版本服务实现
 */
@Slf4j
@Service
public class DocumentServiceImpl implements IDocumentService {

    private final IOnlyOfficeEditService onlyOfficeEditService;
    private final DocumentInfoRepository documentInfoRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentContentRepository documentContentRepository;
    private final DocumentPdfMetaRepository documentPdfMetaRepository;
    private final KafkaDocumentEventPublisher eventPublisher;

    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;

    public DocumentServiceImpl(@Lazy IOnlyOfficeEditService onlyOfficeEditService,
                               DocumentInfoRepository documentInfoRepository,
                               DocumentVersionRepository documentVersionRepository,
                               DocumentContentRepository documentContentRepository,
                               DocumentPdfMetaRepository documentPdfMetaRepository,
                               KafkaDocumentEventPublisher eventPublisher,
                               RemoteStorageService remoteStorageService,
                               RemoteResourceService remoteResourceService) {
        this.onlyOfficeEditService = onlyOfficeEditService;
        this.documentInfoRepository = documentInfoRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentContentRepository = documentContentRepository;
        this.documentPdfMetaRepository = documentPdfMetaRepository;
        this.eventPublisher = eventPublisher;
        this.remoteStorageService = remoteStorageService;
        this.remoteResourceService = remoteResourceService;
    }

    @Override
    public String createDocument(DocumentCreateRequest request, String userId, Map<Long, GroupRoleType> groupRoles) {

        // 向 resource 服务注册 Document 资源
        String resourceId;
        try {
            resourceId = remoteResourceService.createResource(
                    ResourceCreateReqDTO.builder()
                            .resourceName(request.getTitle())
                            .resourceType(request.getResourceType())
                            .ownerId(userId)
                            .ownerGroupRoles(groupRoles)
                            .mountTargetTagId(request.getMountTargetTagId())
                            .build()
            ).getData();
        } catch (Exception e) {
            log.error("document resource register failed. dependency=resourceService", e);
            throw new ServiceException(DocumentError.DOCUMENT_REGISTER_RESOURCE_FAILED, e.getMessage());
        }

        List<Long> authors = new ArrayList<>();
        authors.add(Long.valueOf(userId));

        DocumentInfoEntity infoEntity = DocumentInfoEntity.builder()
                .resourceId(resourceId)
                .authors(authors)
                .version(0)
                .build();
        documentInfoRepository.save(infoEntity);
        return resourceId;
    }

    public DocumentUploadInitResponse initUploadDocument(DocumentUploadInitRequest request, Long uploaderId,
                                                          Map<Long, GroupRoleType> uploaderGroupRoles)  {
        // 普通上传新增版本，检查编辑状态
        return initUploadDocument(request, uploaderId, uploaderGroupRoles, true, true);
    }

    public DocumentUploadInitResponse initUploadDocumentByOnlyOffice(DocumentUploadInitRequest request, Long uploaderId, Boolean isVersioned)  {
        // OnlyOffice 回调上传不检查编辑状态，是否新增版本由调用方决定
        return initUploadDocument(request, uploaderId, null, isVersioned, false);
    }

    private DocumentUploadInitResponse initUploadDocument(DocumentUploadInitRequest request, Long uploaderId,
                                                           Map<Long, GroupRoleType> uploaderGroupRoles, Boolean isVersioned,
                                                           Boolean isCheckEditStatus) {
        ResourceType fileType = ResourceType.fromExtension(request.getExtension());
        if (fileType == null || !DocumentConstants.ALLOWED_TYPES.contains(fileType)) {
            throw new ServiceException(DocumentError.CANNOT_SUPPORT_FILE_TYPE);
        }

        DocumentInfoEntity infoEntity = null;
        if (request.getResourceId() != null) { // 如果 ResourceId 不为空，则说明不是首次上传
            if (isCheckEditStatus) {
                // 未处于在编辑状态
                onlyOfficeEditService.assertNoActiveEditSession(request.getResourceId());
            }
            infoEntity = documentInfoRepository.findByResourceId(request.getResourceId())
                    .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        }


        String documentId = IdUtil.fastSimpleUUID();
        // 向 storage 服务申请预签名直传 URL，以 documentId 作为 bizTag
        UploadInitRespDTO uploadInitRespDTO;
        try {
            uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension(fileType.getExtension())
                    .scene(StorageSceneEnum.PRIVATE_DOC)
                    .bizTag(documentId)
                    .expectedSize(request.getExpectedSize())
                    .isNeedCallback(isVersioned) // 如果要版本化，则需要回调，否则则无需回调
                    .build()).getData();
        } catch (Exception e) {
            log.warn("document upload init failed. documentId={} dependency=storageService", documentId, e);
            throw new ServiceException(DocumentError.DOCUMENT_UPLOAD_URL_APPLY_FAILED, e.getMessage());
        }

        if (isVersioned) {
            DocumentUploadMeta meta = DocumentUploadMeta.builder().fileType(fileType)
                    .documentName(request.getFilename())
                    .size(request.getExpectedSize())
                    .uploaderId(uploaderId)
                    .uploaderGroupRoles(uploaderGroupRoles)
                    .mountTargetTagId(request.getMountTargetTagId())
                    .build();

            DocumentVersionEntity versionEntity = DocumentVersionEntity.builder()
                    .documentId(documentId)
                    .version(1)
                    .uploadMeta(meta)
                    .sourceObjectKey(uploadInitRespDTO.getObjectKey())
                    .documentStatus(new DocumentStatus(
                            uploadInitRespDTO.getFlashUploaded() ?
                                    DocumentStatusEnum.UPLOADED : DocumentStatusEnum.UPLOADING
                    )).build();

            if (infoEntity != null) { // 如果不是第一次上传文档
                Integer nextVersion = infoEntity.getVersion() + 1;
                // 检查版本冲突（未成功的版本）
                documentVersionRepository.findByResourceIdAndVersion(infoEntity.getResourceId(), nextVersion)
                        .ifPresent(existing -> {
                            if (existing.getDocumentStatus() != null && existing.getDocumentStatus().getStatus() == DocumentStatusEnum.READY) {
                                throw new ServiceException(DocumentError.DOCUMENT_VERSION_DUPLICATED);
                            }
                            deletedDocumentVersions(List.of(existing));
                        });

                versionEntity.setVersion(nextVersion);
                versionEntity.setResourceId(infoEntity.getResourceId());
                // 非首次上传时 MountTargetTagId 与 UploaderGroupRoles 字段无效
                versionEntity.getUploadMeta().setMountTargetTagId(null);
                versionEntity.getUploadMeta().setUploaderGroupRoles(null);
            }

            documentVersionRepository.save(versionEntity);
        }

        log.info("document upload initialized. documentId={} objectKey={} flashUploaded={}",
                documentId, uploadInitRespDTO.getObjectKey(), uploadInitRespDTO.getFlashUploaded());

        DocumentUploadInitResponse resp = BeanUtil.copyProperties(uploadInitRespDTO, DocumentUploadInitResponse.class);
        resp.setDocumentId(documentId);

        if (uploadInitRespDTO.getFlashUploaded()) {
            // 如果触发秒传
            eventPublisher.publishParseTask(
                    DocumentParseTaskMessage.builder()
                            .documentId(documentId)
                            .sourceObjectKey(uploadInitRespDTO.getObjectKey())
                            .fileType(fileType)
                            .build()
            );
        }
        return resp;
    }

    @Override
    public List<DocumentVersionBase> listPendingDocs(Long uploaderId) {
        List<DocumentVersionEntity> entities = documentVersionRepository.findByUploaderIdAndStatusIn(
                uploaderId,
                List.of(DocumentStatusEnum.UPLOADING,
                        DocumentStatusEnum.UPLOADED,
                        DocumentStatusEnum.TRANSFER_TIMEOUT,
                        DocumentStatusEnum.REGISTERING_RES_TIMEOUT,
                        DocumentStatusEnum.CONVERTING_AND_PARSING,
                        DocumentStatusEnum.FAILED
                )
        );
        return new ArrayList<>(entities);
    }

    @Override
    public void assertDocumentUploader(String documentId, Long uploaderId) {
        DocumentVersionEntity versionEntity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));

        if (versionEntity.getUploadMeta() == null || !uploaderId.equals(versionEntity.getUploadMeta().getUploaderId())) {
            throw new ServiceException(DocumentError.DOCUMENT_PERMISSION_DENIED);
        }
    }

    @Override
    public void retryDocProcess(String documentId) {
        DocumentVersionEntity entity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));

        if (entity.getDocumentStatus().getStatus() != DocumentStatusEnum.FAILED
                && entity.getDocumentStatus().getStatus() != DocumentStatusEnum.REGISTERING_RES_TIMEOUT) {
            throw new ServiceException(DocumentError.CANNOT_RETRY_DOCUMENT_PROCESS_IN_CURRENT_STATE);
        }
        switch (entity.getDocumentStatus().getStatus()) {
            case FAILED:
                this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.UPLOADED));
                eventPublisher.publishParseTask(
                        DocumentParseTaskMessage.builder()
                                .documentId(entity.getDocumentId())
                                .sourceObjectKey(entity.getSourceObjectKey())
                                .fileType(entity.getUploadMeta().getFileType())
                                .build()
                );
                break;
            case REGISTERING_RES_TIMEOUT:
                this.finalizeToReady(documentId);
                break;
            default:
                break;
        }
        log.info("document retry event dispatched. documentId={} resourceId={} version={} status={}",
                documentId, entity.getResourceId(), entity.getVersion(), entity.getDocumentStatus().getStatus());
    }

    @Override
    public void deletedDocumentVersion(String documentId) {
        DocumentVersionEntity entity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        if (entity.getDocumentStatus().getStatus() == DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.CANNOT_CANCEL_READY_DOCUMENT_PROCESS);
        }

        if (entity.getDocumentStatus().getStatus() == DocumentStatusEnum.CONVERTING_AND_PARSING) {
            throw new ServiceException(DocumentError.CANNOT_CANCEL_DOCUMENT_PROCESS_IN_CURRENT_STATE);
        }

        // NOTE：在CONVERTING_AND_PARSING或READY时不能终止
        // 但由于CONVERTING_AND_PARSING可能意外失败而未能进入READY，仍需清理所有CONVERTING_AND_PARSING可能产生的资产
        deletedDocumentVersions(List.of(entity));
        log.info("document process aborted. documentId={}", documentId);
    }


    private void deletedDocumentVersions(Iterable<DocumentVersionEntity> versionEntities) {
        List<String> documentIds = new ArrayList<>();
        List<String> allObjectKeys = new ArrayList<>();

        for (DocumentVersionEntity versionEntity : versionEntities) {
            documentIds.add(versionEntity.getDocumentId());
            if (StrUtil.isNotBlank(versionEntity.getSourceObjectKey())) {
                allObjectKeys.add(versionEntity.getSourceObjectKey());
            }
            if (StrUtil.isNotBlank(versionEntity.getPreviewObjectKey())) {
                allObjectKeys.add(versionEntity.getPreviewObjectKey());
            }
        }

        // 发送 OSS 文件删除消息
        if (!allObjectKeys.isEmpty()) {
            eventPublisher.publishFileDeleteEvent(allObjectKeys);
        }

        // 一次操作删除所有关联记录
        documentContentRepository.deleteAllById(documentIds);
        documentPdfMetaRepository.deleteAllById(documentIds);
        documentVersionRepository.deleteAllById(documentIds);

        log.info("document versions deleted. count={} documentIds={}", documentIds.size(), summarizeIds(documentIds));
    }

    @Override
    public void deleteDocuments(List<String> resourceIds) {
        // 查询获取所有文档版本
        Iterable<DocumentVersionEntity> versionEntities = documentVersionRepository.findByResourceIdIn(resourceIds);
        deletedDocumentVersions(versionEntities);
        documentInfoRepository.deleteByResourceIdIn(resourceIds);
        log.info("documents deleted. count={} resourceIds={}", resourceIds.size(), summarizeIds(resourceIds));
    }

    @Override
    public Optional<DocumentStatus> getDocumentStatus(String documentId) {
        return documentVersionRepository.findById(documentId).map(DocumentVersionEntity::getDocumentStatus);
    }

    @Override
    public DocumentStatus refreshDocumentStatus(String documentId) {
        DocumentVersionEntity versionEntity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));

        // 如果状态不是正在上传
        if (versionEntity.getDocumentStatus().getStatus() != DocumentStatusEnum.UPLOADING) {
            return versionEntity.getDocumentStatus();
        } else {

            StorageRecordDTO storageRecordDTO;
            try {
                storageRecordDTO = remoteStorageService.getFileRecord(versionEntity.getSourceObjectKey()).getData();
            } catch (Exception e) {
                log.warn("document storage status get failed. documentId={} objectKey={}",
                        documentId, versionEntity.getSourceObjectKey(), e);
                throw new ServiceException(DocumentError.DOCUMENT_STORAGE_STATUS_GET_FAILED, e.getMessage());
            }
            if (storageRecordDTO != null) {
                versionEntity.setDocumentStatus(new DocumentStatus(DocumentStatusEnum.UPLOADED));
                versionEntity.setSourceObjectKey(storageRecordDTO.getObjectKey());
                documentVersionRepository.save(versionEntity);
                eventPublisher.publishParseTask(
                        DocumentParseTaskMessage.builder()
                                .documentId(documentId)
                                .sourceObjectKey(storageRecordDTO.getObjectKey())
                                .fileType(versionEntity.getUploadMeta().getFileType())
                                .build()
                );
            }
            return versionEntity.getDocumentStatus();
        }
    }


    @Override
    public DocumentInfoEntity getDocumentInfo(String resourceId) {
        return documentInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
    }

    @Override
    public DocumentVersionEntity getDocumentVersion(String resourceId, Integer targetVersion) {
        if (targetVersion == null) {
            DocumentInfoEntity resource = documentInfoRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
            targetVersion = resource.getVersion();
        }
        return documentVersionRepository.findByResourceIdAndVersion(resourceId, targetVersion)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
    }

    @Override
    public DocumentSearchTextResponse getDocumentSearchText(String resourceId, Integer targetVersion) {
        DocumentVersionEntity versionEntity = getDocumentVersion(resourceId, targetVersion);
        DocumentContentEntity contentEntity = documentContentRepository.findById(versionEntity.getDocumentId())
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        return DocumentSearchTextResponse.builder()
                .resourceId(resourceId)
                .version(versionEntity.getVersion())
                .searchText(getSearchText(contentEntity))
                .build();
    }

    @Override
    public PageR<DocumentVersionInfoResponse> listVersions(String resourceId, int page, int size) {
        documentInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        Pageable pageable = PageRequest.of(page - 1, size);

        Page<DocumentVersionEntity> entityPage = documentVersionRepository.findByResourceIdOrderByVersionDesc(resourceId, pageable);

        PageR<DocumentVersionInfoResponse> pageR = new PageR<>(entityPage.getTotalElements(), page, size);
        List<DocumentVersionInfoResponse> responses = entityPage.getContent().stream()
                .map(entity->BeanUtil.copyProperties(entity, DocumentVersionInfoResponse.class))
                .toList();
        pageR.addAll(responses);
        return pageR;
    }

    @Override
    public void updateStatus(String documentId, DocumentStatus status) {
        DocumentVersionEntity versionEntity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        DocumentStatusEnum from = versionEntity.getDocumentStatus() != null ? versionEntity.getDocumentStatus().getStatus() : null;
        documentVersionRepository.updateStatusById(documentId, status);
        log.info("document status changed. documentId={} resourceId={} version={} from={} to={}",
                documentId, versionEntity.getResourceId(), versionEntity.getVersion(), from, status.getStatus());
    }

    @Override
    public void saveConversionAndParseResult(String documentId, String previewObjectKey, DocumentPdfMetaEntity meta, DocumentContentEntity content) {
        DocumentVersionEntity entity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        documentVersionRepository.updatePreviewObjectKeyById(documentId, previewObjectKey);

        content.setDocumentId(documentId);
        content.setVersion(entity.getVersion());
        documentContentRepository.save(content);

        meta.setDocumentId(documentId);
        meta.setVersion(entity.getVersion());
        documentPdfMetaRepository.save(meta);
    }

    @Override
    @Transactional
    public void finalizeToReady(String documentId) {
        DocumentVersionEntity versionEntity = documentVersionRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
        if (versionEntity.getDocumentStatus() != null && versionEntity.getDocumentStatus().getStatus() == DocumentStatusEnum.READY) {
            return;
        }

        this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.REGISTERING_RES));

        DocumentInfoEntity infoEntity = null;
        String resourceId = versionEntity.getResourceId();
        if (resourceId != null) { // 非首次上传文档
            infoEntity = documentInfoRepository.findByResourceId(resourceId)
                    .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_NOT_FOUND));
            // 更新 DocumentInfoEntity 的版本号
            documentInfoRepository.updateVersionByResourceId(infoEntity.getResourceId(), infoEntity.getVersion() + 1, LocalDateTime.now());
        } else { // 首次上传文档
            // 向 resource 服务注册资源
            try {
                resourceId = remoteResourceService.createResource(
                        ResourceCreateReqDTO.builder()
                                .resourceName(versionEntity.getUploadMeta().getDocumentName())
                                .resourceType(versionEntity.getUploadMeta().getFileType())
                                .ownerId(versionEntity.getUploadMeta().getUploaderId().toString())
                                .ownerGroupRoles(versionEntity.getUploadMeta().getUploaderGroupRoles())
                                .size(versionEntity.getUploadMeta().getSize())
                                .mountTargetTagId(versionEntity.getUploadMeta().getMountTargetTagId())
                                .build()
                ).getData();
            } catch (Exception e) {
                log.error("document resource register failed. documentId={}", documentId, e);
                this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.REGISTERING_RES_TIMEOUT));
                throw new ServiceException(DocumentError.DOCUMENT_REGISTER_RESOURCE_FAILED, e.getMessage());
            }
            // 新建 DocumentInfoEntity
            infoEntity = DocumentInfoEntity.builder().resourceId(resourceId).version(1).build();
            documentInfoRepository.save(infoEntity);
            // 更新 ResourceId
            documentVersionRepository.updateResourceIdById(documentId, resourceId);
        }

        this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.READY));

        String readyContent = documentContentRepository.findById(documentId)
                .map(DocumentServiceImpl::getSearchText)
                .orElse(null);
        eventPublisher.publishReadyEvent(DocumentReadyMessage.builder()
                .resourceId(resourceId)
                .version(versionEntity.getVersion())
                .content(readyContent)
                .build());

        log.debug("document ready finalized. documentId={} resourceId={}", documentId, resourceId);
    }

    @Override
    @Transactional
    public String forkDocument(DocumentForkRequest request, String forkedResourceOwnerId) {
        DocumentInfoEntity documentInfo = getDocumentInfo(request.getResourceId());
        Integer effectiveVersion = request.getForkedResourceVersion() != null ? request.getForkedResourceVersion() : documentInfo.getVersion();
        // 版本号为 0 时不能 Fork
        if (Integer.valueOf(0).equals(effectiveVersion)) throw new ServiceException(DOCUMENT_HAS_NO_VERSION);

        String targetDocumentId = IdUtil.fastSimpleUUID();
        // 检索待复制项
        DocumentVersionEntity sourceVersion = getDocumentVersion(request.getResourceId(), request.getForkedResourceVersion());
        // 待复制项必须已经就绪
        if (sourceVersion.getDocumentStatus() == null || sourceVersion.getDocumentStatus().getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_NOT_READY);
        }

        List<String> copiedObjectKeys = new ArrayList<>();
        String resourceId = null;
        try {
            // 复制 Source Object
            StorageRecordDTO copied = remoteStorageService.copyFile(CopyReqDTO.builder()
                    .sourceObjectKey(sourceVersion.getSourceObjectKey())
                    .scene(StorageSceneEnum.PRIVATE_DOC)
                    .bizTag(targetDocumentId)
                    .build()).getData();
            copiedObjectKeys.add(copied.getObjectKey());

            // 复制 Preview Object
            copied = remoteStorageService.copyFile(CopyReqDTO.builder()
                    .sourceObjectKey(sourceVersion.getPreviewObjectKey())
                    .scene(StorageSceneEnum.PRIVATE_DOC)
                    .bizTag(targetDocumentId)
                    .build()).getData();
            copiedObjectKeys.add(copied.getObjectKey());

            // 复制 文档内容
            DocumentContentEntity sourceContent = documentContentRepository.findById(sourceVersion.getDocumentId())
                    .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_FORK_FAILED));
            DocumentContentEntity targetContent = BeanUtil.copyProperties(sourceContent, DocumentContentEntity.class, "createTime");
            targetContent.setDocumentId(targetDocumentId);
            targetContent.setVersion(1);
            documentContentRepository.save(targetContent);

            // 复制 Pdf Meta 信息
            DocumentPdfMetaEntity sourcePdfMeta = documentPdfMetaRepository.findById(sourceVersion.getDocumentId())
                    .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_FORK_FAILED));
            DocumentPdfMetaEntity targetPdfMeta = BeanUtil.copyProperties(sourcePdfMeta, DocumentPdfMetaEntity.class);
            targetPdfMeta.setDocumentId(targetDocumentId);
            targetPdfMeta.setVersion(1);
            documentPdfMetaRepository.save(targetPdfMeta);

            // 建立文档元信息
            DocumentVersionEntity targetVersion = DocumentVersionEntity.builder()
                    .documentId(targetDocumentId)
                    .version(1)
                    .sourceObjectKey(copiedObjectKeys.getFirst())
                    .previewObjectKey(copiedObjectKeys.getLast())
                    .uploadMeta(sourceVersion.getUploadMeta())
                    .documentStatus(new DocumentStatus(DocumentStatusEnum.READY))
                    .maxPreviewPages(sourceVersion.getMaxPreviewPages())
                    .build();
            documentVersionRepository.save(targetVersion);

            // 向 resource 服务注册 Forked 资源
            try {
                resourceId = remoteResourceService.createResource(
                        ResourceCreateReqDTO.builder()
                                .resourceName(request.getForkedResourceName())
                                .resourceType(sourceVersion.getUploadMeta().getFileType())
                                .ownerId(forkedResourceOwnerId)
                                .size(sourceVersion.getUploadMeta().getSize())
                                .build()
                ).getData();
            } catch (Exception e) {
                log.error("document resource register failed. dependency=resourceService", e);
                throw new ServiceException(DocumentError.DOCUMENT_REGISTER_RESOURCE_FAILED, e.getMessage());
            }

            documentVersionRepository.updateResourceIdById(targetDocumentId, resourceId);

            // 新建 DocumentInfoEntity
            DocumentInfoEntity targetInfo = DocumentInfoEntity.builder().resourceId(resourceId).version(1).build();
            documentInfoRepository.save(targetInfo);

            // 发送文档就绪事件
            eventPublisher.publishReadyEvent(DocumentReadyMessage.builder()
                    .resourceId(resourceId)
                    .version(1)
                    .content(getSearchText(targetContent))
                    .build());
            log.info("document fork finished. sourceResourceId={} sourceVersion={} resourceId={} documentId={}",
                    request.getResourceId(), sourceVersion.getVersion(), resourceId, targetDocumentId);

            return resourceId;

        } catch (Exception e) {
            // 异常时回滚
            if (resourceId != null) {
                documentContentRepository.deleteById(targetDocumentId);
                documentPdfMetaRepository.deleteById(targetDocumentId);
                documentVersionRepository.deleteById(targetDocumentId);
                documentInfoRepository.deleteById(resourceId);
            }
            if (!copiedObjectKeys.isEmpty()) {
                eventPublisher.publishFileDeleteEvent(copiedObjectKeys);
            }
            log.warn("document fork compensated. sourceResourceId={} documentId={}",
                    request.getResourceId(), targetDocumentId, e);
            throw new ServiceException(DocumentError.DOCUMENT_FORK_FAILED, e.getMessage());
        }
    }

    private static String getSearchText(DocumentContentEntity contentEntity) {
        return contentEntity.getMarkdown() != null ? contentEntity.getMarkdown() : contentEntity.getRawText();
    }
}
