package com.oriole.wisepen.document.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文档下载服务：返回原始文件的 OSS 预签名 URL 并重定向。
 *
 * <p>仅 {@code READY} 状态的文档可下载。
 * 下载内容为用户上传的原始文件（sourceObjectKey），不含水印处理。
 *
 * @author Ian.xiong
 */
public interface IDocumentDownloadService {

    /**
     * 处理下载请求：校验文档状态，获取 OSS 预签名 URL，302 重定向至 OSS。
     *
     * @param request    HTTP 请求
     * @param response   HTTP 响应
     * @param documentId 文档唯一 ID
     * @param userId     当前用户 ID（用于日志）
     */
    void handleDownloadRequest(HttpServletRequest request,
                               HttpServletResponse response,
                               String documentId,
                               String userId);
}
