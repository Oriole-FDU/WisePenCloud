package com.oriole.wisepen.document.service;

import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageResolveResponse;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;

import java.io.File;
import java.util.List;

public interface IDocumentAssetService {

    String processMarkdown(String markdown, File pdfFile, String documentId);

    DocumentImageReferencesResponse listImageReferences(DocumentVersionEntity version, Integer pageNo);

    DocumentImageResolveResponse resolveImage(DocumentVersionEntity version, String assetRef);

    String copyMarkdownAssets(String markdown, String sourceDocumentId, String targetDocumentId, List<String> copiedKeys);
}
