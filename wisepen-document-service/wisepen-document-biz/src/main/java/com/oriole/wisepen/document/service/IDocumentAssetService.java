package com.oriole.wisepen.document.service;

import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageResolveResponse;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;

import java.io.File;
import java.util.List;

public interface IDocumentAssetService {

    /** 转换期处理正文：内嵌图片外置上传、注入页边界标记，返回重写后的 Markdown。 */
    String processMarkdown(String markdown, File pdfFile, String documentId);

    /** 列出该版本正文引用的平台图片，可按页过滤。 */
    DocumentImageReferencesResponse listImageReferences(DocumentVersionEntity version, Integer pageNo);

    /** 为正文引用的一张图片申请限时下载地址。 */
    DocumentImageResolveResponse resolveImage(DocumentVersionEntity version, String assetRef);

    /** Fork 时逐图复制到目标版本目录，返回重写后的 Markdown；成功复制的 key 追加进 copiedKeys。 */
    String copyMarkdownAssets(String markdown, String sourceDocumentId, String targetDocumentId, List<String> copiedKeys);
}
