package com.oriole.wisepen.document.service;

import com.oriole.wisepen.document.api.enums.DocumentDownloadType;
import com.oriole.wisepen.resource.enums.ResourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IDocumentPreviewService {
    // 处理预览请求
    void handlePreviewRequest(HttpServletRequest request,
                              HttpServletResponse response,
                              String resourceId,
                              Integer targetVersion,
                              String userId);

    // 处理下载请求
    void handleDownloadRequest(HttpServletRequest request,
                               HttpServletResponse response,
                               String resourceId,
                               Integer targetVersion,
                               String userId,
                               DocumentDownloadType downloadType);
}
