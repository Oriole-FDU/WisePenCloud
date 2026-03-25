package com.oriole.wisepen.document.service.impl;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.exception.DocumentErrorCode;
import com.oriole.wisepen.document.service.IDocumentPreviewService;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import com.oriole.wisepen.document.util.WatermarkAppendixBuilder;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.enums.ResPermissionLevelEnum;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档预览服务实现：O(1) 预埋 + Range Request 劫持模式。
 *
 * <h3>虚拟文件布局</h3>
 * <pre>
 *   byte 0 ────────────── originalSize-1   ← OSS 预埋 PDF（含空 /WisepenWM 占位符）
 *   byte originalSize ─── totalSize-1      ← 动态水印增量附录（真实 userId + 时间戳）
 * </pre>
 *
 * <h3>Range 路由</h3>
 * <ul>
 *   <li>落在原始段：向 OSS 发起同 Range 请求，InputStream → OutputStream 管道透传（零内存拷贝）。</li>
 *   <li>落在附录段：动态生成 appendix byte[]，按偏移切片写出。</li>
 *   <li>跨界：先透传 OSS 尾部，再写附录头部。</li>
 * </ul>
 *
 * @author Ian.xiong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPreviewServiceImpl implements IDocumentPreviewService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** RFC 7233：Range: bytes=A-B */
    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private static final int PIPE_BUF = 64 * 1024; // 64 KB 管道缓冲区

    private final IDocumentProcessService documentProcessService;
    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;
    private final DocumentProperties documentProperties;

    @Override
    public void handlePreviewRequest(HttpServletRequest request,
                                     HttpServletResponse response,
                                     String documentId,
                                     String userId) {
        DocumentInfoEntity doc = documentProcessService.getDocumentInfo(documentId);
        if (doc == null || doc.getStatus() != DocumentStatusEnum.READY) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        // 权限校验：调 resource 服务实时计算用户是否有权访问此文档
        ResourceCheckPermissionReqDTO permReq = new ResourceCheckPermissionReqDTO();
        permReq.setResourceId(documentId);
        permReq.setResourceType(doc.getFileType().name());
        permReq.setUserId(userId);
        permReq.setGroupRoles(SecurityContextHolder.getGroupRoleMap());
        ResPermissionLevelEnum permLevel = remoteResourceService.checkResPermission(permReq).getData().getResPermissionLevel();
        if (permLevel.getLevel() < ResPermissionLevelEnum.GROUP_MEMBER.getLevel()) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_PERMISSION_DENIED);
        }

        DocumentPdfMetaEntity meta = documentProcessService.getPdfMeta(documentId);
        if (meta == null || meta.getAppendixSize() == 0) {
            // 元数据尚未就绪（极少数情况），降级为 404
            log.warn("PDF 元数据不可用，降级 404: documentId={}", documentId);
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        long originalSize = meta.getOriginalSize();
        long totalSize = originalSize + meta.getAppendixSize();

        String ossUrl = remoteStorageService.getDownloadUrl(doc.getPreviewObjectKey(), null).getData();

        // 在时间戳确定之前生成附录，保证同一请求内明/暗水印时间一致
        LocalDateTime previewTime = LocalDateTime.now();

        response.setContentType("application/pdf");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "no-store");

        try {
            String rangeHeader = request.getHeader("Range");
            if (rangeHeader == null) {
                // 全量请求：OSS 原始段 + 附录
                response.setHeader("Content-Length", String.valueOf(totalSize));
                response.setStatus(HttpStatus.OK.value());
                ServletOutputStream out = response.getOutputStream();
                pipeOssRange(ossUrl, 0, originalSize - 1, out);
                out.write(buildAppendix(meta, userId, previewTime));
            } else {
                handleRangeRequest(rangeHeader, totalSize, originalSize,
                        ossUrl, meta, userId, previewTime, response);
            }
        } catch (IOException e) {
            log.error("预览响应写入失败: documentId={}", documentId, e);
            // 响应已开始写出，无法再设置状态码，仅记录日志
        } catch (Exception e) {
            log.error("预览请求处理失败: documentId={}", documentId, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }
    }

    // =========================================================================
    //  Range 处理
    // =========================================================================

    private void handleRangeRequest(String rangeHeader,
                                     long totalSize, long originalSize,
                                     String ossUrl,
                                     DocumentPdfMetaEntity meta,
                                     String userId, LocalDateTime previewTime,
                                     HttpServletResponse response) throws Exception {
        Matcher m = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!m.matches()) {
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            response.setHeader("Content-Range", "bytes */" + totalSize);
            return;
        }

        long start = Long.parseLong(m.group(1));
        long end = m.group(2).isEmpty() ? totalSize - 1 : Long.parseLong(m.group(2));

        if (start > end || start >= totalSize) {
            response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
            response.setHeader("Content-Range", "bytes */" + totalSize);
            return;
        }
        end = Math.min(end, totalSize - 1);

        response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
        response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
        response.setHeader("Content-Length", String.valueOf(end - start + 1));

        ServletOutputStream out = response.getOutputStream();

        if (end < originalSize) {
            // 情形 1：完全落在原始段 → 零拷贝透传 OSS
            pipeOssRange(ossUrl, start, end, out);

        } else if (start >= originalSize) {
            // 情形 2：完全落在附录段 → 内存生成后切片
            byte[] appendix = buildAppendix(meta, userId, previewTime);
            int offset = (int) (start - originalSize);
            int length = (int) (end - start + 1);
            out.write(appendix, offset, length);

        } else {
            // 情形 3：跨越两段边界 → OSS 尾部 + 附录头部
            pipeOssRange(ossUrl, start, originalSize - 1, out);
            byte[] appendix = buildAppendix(meta, userId, previewTime);
            int lengthInAppendix = (int) (end - originalSize + 1);
            out.write(appendix, 0, lengthInAppendix);
        }
    }

    // =========================================================================
    //  OSS 代理
    // =========================================================================

    /**
     * 向 OSS 发起 Range 请求，将响应体以 64 KB 缓冲管道写入 {@code out}（零内存拷贝）。
     */
    private void pipeOssRange(String ossUrl, long rangeStart, long rangeEnd,
                               OutputStream out) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ossUrl))
                .header("Range", "bytes=" + rangeStart + "-" + rangeEnd)
                .GET()
                .build();

        HttpResponse<InputStream> resp = HTTP_CLIENT.send(req,
                HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() != 206 && resp.statusCode() != 200) {
            throw new IllegalStateException("OSS Range 请求失败: status=" + resp.statusCode());
        }

        byte[] buf = new byte[PIPE_BUF];
        try (InputStream in = resp.body()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    // =========================================================================
    //  附录生成
    // =========================================================================

    private byte[] buildAppendix(DocumentPdfMetaEntity meta,
                                  String userId,
                                  LocalDateTime time) {
        try {
            return WatermarkAppendixBuilder.build(meta, userId, time,
                    documentProperties.getWatermarkSecretKey());
        } catch (Exception e) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_READ_ERROR);
        }
    }
}
