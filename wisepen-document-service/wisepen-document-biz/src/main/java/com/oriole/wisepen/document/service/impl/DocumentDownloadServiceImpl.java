package com.oriole.wisepen.document.service.impl;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.exception.DocumentErrorCode;
import com.oriole.wisepen.document.service.IDocumentDownloadService;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.enums.ResPermissionLevelEnum;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 文档下载服务实现：302 重定向至 OSS 预签名 URL。
 *
 * <p>生成一个 900 秒有效期的防盗链预签名 URL，通过 HTTP 302 重定向让客户端
 * 直接从 OSS 拉取原始文件，服务器不透传字节流，带宽占用为零。
 *
 * @author Ian.xiong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDownloadServiceImpl implements IDocumentDownloadService {

    /** 下载链接默认有效时长（秒） */
    private static final long DEFAULT_DOWNLOAD_DURATION = 900L;

    private final IDocumentProcessService documentProcessService;
    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;

    @Override
    public void handleDownloadRequest(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String documentId,
                                      String userId) {
        // 1. 查文档，校验状态
        DocumentInfoEntity doc = documentProcessService.getDocumentInfo(documentId);
        if (doc == null) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (doc.getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_NOT_READY);
        }

        // 权限校验：仅文档所有者（OWNER）可下载原始文件
        ResourceCheckPermissionReqDTO permReq = new ResourceCheckPermissionReqDTO();
        permReq.setResourceId(documentId);
        permReq.setResourceType(doc.getFileType().name());
        permReq.setUserId(userId);
        permReq.setGroupRoles(SecurityContextHolder.getGroupRoleMap());
        ResPermissionLevelEnum permLevel = remoteResourceService.checkResPermission(permReq).getData().getResPermissionLevel();
        if (permLevel.getLevel() < ResPermissionLevelEnum.OWNER.getLevel()) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_PERMISSION_DENIED);
        }

        // 2. 向 storage 服务申请原始文件的预签名下载 URL
        R<String> result = remoteStorageService.getDownloadUrl(doc.getSourceObjectKey(), DEFAULT_DOWNLOAD_DURATION);
        if (result.getCode() != 200 || result.getData() == null) {
            log.error("获取下载 URL 失败: documentId={}, userId={}, storageResp={}", documentId, userId, result);
            throw new ServiceException(DocumentErrorCode.DOCUMENT_DOWNLOAD_ERROR);
        }

        // 3. 302 重定向，让客户端直接去 OSS 拉取原始文件
        log.info("文档下载重定向: documentId={}, userId={}", documentId, userId);
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", result.getData());
    }

     
}
