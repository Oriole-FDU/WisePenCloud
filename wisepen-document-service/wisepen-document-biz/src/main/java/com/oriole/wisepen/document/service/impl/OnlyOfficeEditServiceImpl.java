package com.oriole.wisepen.document.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.onlyoffice.client.ApacheHttpclientDocumentServerClient;
import com.onlyoffice.client.DocumentServerClient;
import com.onlyoffice.client.DocumentServerClientSettings;
import com.onlyoffice.model.common.User;
import com.onlyoffice.model.documenteditor.Callback;
import com.onlyoffice.model.documenteditor.Config;
import com.onlyoffice.model.documenteditor.callback.Action;
import com.onlyoffice.model.documenteditor.config.Document;
import com.onlyoffice.model.documenteditor.config.EditorConfig;
import com.onlyoffice.model.documenteditor.config.document.DocumentType;
import com.onlyoffice.model.documenteditor.config.document.Permissions;
import com.onlyoffice.model.documenteditor.config.editorconfig.Customization;
import com.onlyoffice.model.documenteditor.config.editorconfig.Mode;
import com.onlyoffice.model.documenteditor.config.editorconfig.customization.Customer;
import com.onlyoffice.model.settings.security.Security;
import com.onlyoffice.utils.SecurityUtils;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.api.domain.dto.res.OnlyOfficeEditorConfigResponse;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentEditSessionEntity;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.document.api.enums.DocumentEditSessionStatus;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.repository.DocumentEditSessionRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.document.service.IOnlyOfficeEditService;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static com.oriole.wisepen.document.api.constant.DocumentConstants.ONLY_OFFICE_CALLBACK_PATH;

@Slf4j
@Service
public class OnlyOfficeEditServiceImpl implements IOnlyOfficeEditService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final IDocumentService documentService;
    private final DocumentEditSessionRepository editSessionRepository;
    private final RemoteStorageService remoteStorageService;
    private final DocumentProperties documentProperties;
    private final KafkaDocumentEventPublisher eventPublisher;
    private final DocumentServerClient documentServerClient;

    public OnlyOfficeEditServiceImpl(IDocumentService documentService,
                                     DocumentEditSessionRepository editSessionRepository,
                                     RemoteStorageService remoteStorageService,
                                     DocumentProperties documentProperties,
                                     KafkaDocumentEventPublisher eventPublisher) {
        this.documentService = documentService;
        this.editSessionRepository = editSessionRepository;
        this.remoteStorageService = remoteStorageService;
        this.documentProperties = documentProperties;
        this.eventPublisher = eventPublisher;
        this.documentServerClient = new ApacheHttpclientDocumentServerClient(
                DocumentServerClientSettings.builder()
                        .baseUrl(documentProperties.getOnlyofficeInternalUrl())
                        .security(Security.builder()
                                .key(documentProperties.getOnlyofficeJwtSecret())
                                .header(documentProperties.getOnlyofficeJwtHeader())
                                .prefix(documentProperties.getOnlyofficeJwtPrefix())
                                .build())
                        .ignoreSSLCertificate(false)
                        .build()
        );
    }

    @Override
    public OnlyOfficeEditorConfigResponse getEditorConfig(String resourceId, Long userId, ResourceCheckPermissionResDTO permissionResDTO, UserDisplayBase userDisplayInfo) {
        DocumentInfoEntity infoEntity = documentService.getDocumentInfo(resourceId);
        DocumentVersionEntity versionEntity = documentService.getDocumentVersion(resourceId, infoEntity.getVersion());

        if (versionEntity.getDocumentStatus() == null || versionEntity.getDocumentStatus().getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_NOT_READY);
        }

        ResourceType fileType = versionEntity.getUploadMeta().getFileType();

        Set<ResourceType> editableOfficeType = Set.of(ResourceType.DOC, ResourceType.DOCX, ResourceType.PPT, ResourceType.PPTX, ResourceType.XLS, ResourceType.XLSX);
        if (!editableOfficeType.contains(fileType)) {
            throw new ServiceException(DocumentError.DOCUMENT_EDIT_NOT_SUPPORTED);
        }

        DocumentEditSessionEntity session = editSessionRepository.findActiveByResourceId(
                resourceId,
                List.of(DocumentEditSessionStatus.ACTIVE, DocumentEditSessionStatus.DRAFT_SAVED, DocumentEditSessionStatus.SAVING),
                LocalDateTime.now()
        ).orElseGet(() -> {
            String sessionId = IdUtil.fastSimpleUUID();
            String documentKey = resourceId + "_v" + versionEntity.getVersion() + "_" + sessionId;
            DocumentEditSessionEntity entity = DocumentEditSessionEntity.builder()
                    .sessionId(sessionId).resourceId(resourceId).documentKey(documentKey)
                    .baseDocumentId(versionEntity.getDocumentId()).baseVersion(versionEntity.getVersion())
                    .fileType(versionEntity.getUploadMeta().getFileType())
                    .documentName(versionEntity.getUploadMeta().getDocumentName())
                    .sourceObjectKey(versionEntity.getSourceObjectKey())
                    .initiatorUserId(userId)
                    .status(DocumentEditSessionStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusSeconds(documentProperties.getOnlyofficeEditSessionTtlSeconds()))
                    .build();
            return editSessionRepository.save(entity);
        });

        return OnlyOfficeEditorConfigResponse.builder()
                .sessionId(session.getSessionId())
                .config(buildEditorConfig(session, versionEntity, userId, permissionResDTO, userDisplayInfo))
                .build();
    }

    private Config buildEditorConfig(DocumentEditSessionEntity session, DocumentVersionEntity versionEntity,
                                                  Long userId, ResourceCheckPermissionResDTO permissionResDTO, UserDisplayBase userDisplayInfo) {
        // 申请下载 URL
        String sourceUrl;
        try {
            sourceUrl = remoteStorageService.getDownloadUrl(
                    versionEntity.getSourceObjectKey(),
                    documentProperties.getOnlyofficeSourceUrlDurationSeconds(),
                    null
            ).getData();
        } catch (Exception exception){
            throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED);
        }

        List<ResourceAction> allowedActions = permissionResDTO.getAllowedActions();

        // 构建权限
        Permissions permissions = Permissions.builder()
                .chat(false)
                .copy(allowedActions.contains(ResourceAction.VIEW))
                .edit(allowedActions.contains(ResourceAction.EDIT))
                .comment(allowedActions.contains(ResourceAction.INLINE_COMMENT))
                .download(allowedActions.contains(ResourceAction.DOWNLOAD_ORIGINAL))
                .print(allowedActions.contains(ResourceAction.DOWNLOAD_ORIGINAL))
                .build();

        // 构建文档
        Document document = Document.builder()
                .fileType(session.getFileType().getExtension())
                .key(session.getDocumentKey())
                .title(versionEntity.getUploadMeta().getDocumentName())
                .url(sourceUrl)
                .permissions(permissions)
                .build();

        // 构建用户
        User user = User.builder()
                .id(userId.toString())
                .name(userDisplayInfo != null ? userDisplayInfo.getNickname() : "User")
                .build();

        // 构建定制化
        Customization customization = Customization.builder()
                .plugins(false)
                .help(false)
                .feedback(false)
                .forcesave(true)
                .compactHeader(true)
                .compactToolbar(false)
                .toolbarHideFileName(false)
                .customer(Customer.builder().name("WisePen").build())
                .build();

        // 构建回调 URL
        String callbackUrl = documentProperties.getOnlyofficeCallbackBaseUrl() + ONLY_OFFICE_CALLBACK_PATH  + session.getSessionId();

        // 构建 editorConfig
        EditorConfig editorConfig = EditorConfig.builder()
                .mode(Mode.EDIT)
                .lang("zh-CN")
                .callbackUrl(callbackUrl)
                .user(user)
                .customization(customization)
                .build();

        DocumentType documentType = switch (session.getFileType()) {
            case PPT, PPTX -> DocumentType.SLIDE;
            case XLS, XLSX -> DocumentType.CELL;
            default -> DocumentType.WORD;
        };

        Config config = Config.builder()
                .documentType(documentType)
                .document(document)
                .editorConfig(editorConfig)
                .build();

        config.setToken(SecurityUtils.createToken(config, documentProperties.getOnlyofficeJwtSecret()));
        return config;
    }


    @Override
    public Map<String, Integer> handleCallback(String sessionId, Callback request) {
        DocumentEditSessionEntity session = editSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_EDIT_SESSION_NOT_FOUND));

        // 校验回调
        try {
            SecurityUtils.verifyToken(request.getToken(), documentProperties.getOnlyofficeJwtSecret());
        } catch (Exception e) {
            throw new ServiceException(DocumentError.DOCUMENT_EDIT_CALLBACK_INVALID);
        }

        List<Action> actions = request.getActions();
        String userId = actions.getFirst() != null ? actions.getFirst().getUserid() : request.getUsers().getFirst();
        try {
            switch (request.getStatus()){
                case SAVE -> saveFile(session, request.getUrl(), userId, true); // 保存最终版本
                case FORCESAVE -> saveFile(session, request.getUrl(), userId, false); // 保存草稿
                case CLOSED -> { // 没有任何更改，转为结束即可
                    session.setStatus(DocumentEditSessionStatus.FINISHED);
                    editSessionRepository.save(session);
                }
                case SAVE_CORRUPTED, FORCESAVE_CORRUPTED -> { // 错误
                    session.setStatus(DocumentEditSessionStatus.FAILED);
                    editSessionRepository.save(session);
                    throw new ServiceException(DocumentError.DOCUMENT_EDIT_SAVE_FAILED);
                }
            }
            return Map.of("error", 0);
        } catch (Exception e) {
            log.error("onlyoffice callback handling failed. resourceId={} status={}", session.getResourceId(), request.getStatus(), e);
            session.setStatus(DocumentEditSessionStatus.FAILED);
            session.setErrorMessage(e.getMessage());
            editSessionRepository.save(session);
            throw new ServiceException(DocumentError.DOCUMENT_EDIT_SAVE_FAILED, e.getMessage());
        }
    }

    private void saveFile(DocumentEditSessionEntity session, String callbackUrl, String saveUser, Boolean isFinalFile) throws Exception {
        if (isFinalFile) {
            session.setStatus(DocumentEditSessionStatus.SAVING);
            editSessionRepository.save(session);
        }

        String uploadObjectKey = handleCallbackFile(callbackUrl, session, saveUser, isFinalFile);
        session.setLatestDraftObjectKey(uploadObjectKey);
        session.setStatus(isFinalFile ? DocumentEditSessionStatus.FINISHED : DocumentEditSessionStatus.DRAFT_SAVED);
        editSessionRepository.save(session);

    }

    private String handleCallbackFile(String callbackUrl, DocumentEditSessionEntity session, String saveUser, Boolean isVersioned) throws Exception {
        File file = null;
        try {
            // 构建临时文件夹与临时文件
            Path dir = Path.of(documentProperties.getCachePath());
            Files.createDirectories(dir);
            file = Files.createTempFile(dir, session.getResourceId() + "_edit", "." + session.getFileType().getExtension()).toFile();

            // 下载临时文件
            try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
                documentServerClient.getFile(callbackUrl, outputStream);
            }

            // 上传临时文件
            DocumentUploadInitRequest uploadRequest = DocumentUploadInitRequest.builder()
                    .resourceId(session.getResourceId()).filename(session.getDocumentName())
                    .extension(session.getFileType().getExtension()).md5(null)
                    .expectedSize(file.length()).build();
            DocumentUploadInitResponse uploadInitRespDTO = documentService.initUploadDocumentByOnlyOffice(uploadRequest, Long.parseLong(saveUser), isVersioned);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(uploadInitRespDTO.getPutUrl()))
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofFile(file.toPath()));
            if (StrUtil.isNotBlank(uploadInitRespDTO.getCallbackHeader())) {
                reqBuilder.header("x-oss-callback", uploadInitRespDTO.getCallbackHeader());
            }
            HttpResponse<Void> uploadRes = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
            if (uploadRes.statusCode() / 100 != 2) {
                throw new ServiceException(DocumentError.DOCUMENT_EDIT_SAVE_FAILED, "edited file upload failed. status=" + uploadRes.statusCode());
            }

            return uploadInitRespDTO.getObjectKey();
        } finally {
            if (file != null && file.exists()) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void assertNoActiveEditSession(String resourceId) {
        boolean active = editSessionRepository.findActiveByResourceId(
                resourceId,
                List.of(DocumentEditSessionStatus.ACTIVE, DocumentEditSessionStatus.DRAFT_SAVED, DocumentEditSessionStatus.SAVING),
                LocalDateTime.now()
        ).isPresent();
        if (active) {
            throw new ServiceException(DocumentError.DOCUMENT_EDIT_SESSION_ACTIVE);
        }
    }
}
