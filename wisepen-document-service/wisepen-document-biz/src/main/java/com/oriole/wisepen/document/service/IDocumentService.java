package com.oriole.wisepen.document.service;

import com.oriole.wisepen.document.api.domain.base.DocumentVersionBase;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentCreateRequest;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentForkRequest;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentVersionInfoResponse;
import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IDocumentService {

    // 断言文档的所属权 (仅对未就绪文档有效)
    void assertDocumentUploader(String documentId, Long uploaderId);

    // 新建文档
    String createDocument(DocumentCreateRequest request, String userId, Map<Long, GroupRoleType> uploaderGroupRoles);

    // 初始化上传
    DocumentUploadInitResponse initUploadDocument(DocumentUploadInitRequest request, Long uploaderId,
                                                   Map<Long, GroupRoleType> uploaderGroupRoles);

    // 初始化上传（OnlyOffice）
    DocumentUploadInitResponse initUploadDocumentByOnlyOffice(DocumentUploadInitRequest request, Long uploaderId, Boolean isVersioned);

    // 获取未就绪文档列表
    List<DocumentVersionBase> listPendingDocs(Long uploaderId);

    // 获取文档状态
    Optional<DocumentStatus> getDocumentStatus(String documentId);

    // 刷新/获取文档状态
    DocumentStatus refreshDocumentStatus(String documentId);

    // 重试文档处理
    void retryDocProcess(String documentId);

    // 删除文档版本
    void deletedDocumentVersion(String documentId);

    // 删除文档
    void deleteDocuments(List<String> resourceIds);

    // 获取文档信息
    DocumentInfoEntity getDocumentInfo(String resourceId);

    // 获取指定版本信息
    DocumentVersionEntity getDocumentVersion(String resourceId, Integer targetVersion);

    // 获取指定版本搜索文本
    DocumentSearchTextResponse getDocumentSearchText(String resourceId, Integer targetVersion);

    // 分页查询文档版本
    PageR<DocumentVersionInfoResponse> listVersions(String resourceId, int page, int size);

    // 更新文档状态
    void updateStatus(String documentId, DocumentStatus status);

    // 归档文档解析的结果
    void saveConversionAndParseResult(String documentId, String previewObjectKey, DocumentPdfMetaEntity meta, DocumentContentEntity content);

    // 文档就绪
    void finalizeToReady(String documentId);

    // 复制文档
    String forkDocument(DocumentForkRequest request, String forkedResourceOwnerId);
}
