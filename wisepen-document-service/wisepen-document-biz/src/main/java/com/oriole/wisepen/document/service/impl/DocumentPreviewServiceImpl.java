package com.oriole.wisepen.document.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.domain.base.DocumentUploadMeta;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.api.enums.DocumentDownloadType;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.domain.entity.DocumentVersionEntity;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.document.repository.DocumentPdfMetaRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.document.service.IDocumentPreviewService;
import com.oriole.wisepen.document.util.WatermarkAppendixBuilder;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

    private final DocumentPdfMetaRepository documentPdfMetaRepository;
    private final IDocumentService documentService;

    private final RemoteStorageService remoteStorageService;
    private final DocumentProperties documentProperties;

    @Override
    public void handlePreviewRequest(HttpServletRequest request,
                                     HttpServletResponse response,
                                     String resourceId,
                                     Integer targetVersion,
                                     String userId) {
        handleWatermarkPdfRequest(request, response, resourceId, targetVersion, userId, false);
    }

    @Override
    public void handleDownloadRequest(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String resourceId,
                                      Integer targetVersion,
                                      String userId,
                                      DocumentDownloadType downloadType) {
        if (DocumentDownloadType.ORIGINAL == downloadType) {
            handleOriginalDownloadRequest(response, resourceId, targetVersion);
            return;
        }
        handleWatermarkPdfRequest(request, response, resourceId, targetVersion, userId, true);
    }

    private void handleWatermarkPdfRequest(HttpServletRequest request,
                                           HttpServletResponse response,
                                           String resourceId,
                                           Integer targetVersion,
                                           String userId,
                                           boolean attachment) {
        DocumentInfoEntity infoEntity = documentService.getDocumentInfo(resourceId);
        targetVersion = targetVersion != null ? targetVersion : infoEntity.getVersion();
        if (Integer.valueOf(0).equals(targetVersion)) {
            throw new ServiceException(DocumentError.DOCUMENT_HAS_NO_VERSION);
        }

        DocumentVersionEntity versionEntity = documentService.getDocumentVersion(resourceId, targetVersion);

        if (versionEntity.getDocumentStatus() == null || versionEntity.getDocumentStatus().getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_NOT_READY);
        }

        DocumentPdfMetaEntity meta = documentPdfMetaRepository.findById(versionEntity.getDocumentId())
                .orElseThrow(() -> new ServiceException(DocumentError.DOCUMENT_PREVIEW_META_NOT_FOUND));

        long originalSize = meta.getOriginalSize();
        long totalSize = originalSize + meta.getAppendixSize();

        String ossUrl;
        try {
            ossUrl = remoteStorageService.getDownloadUrl(
                    versionEntity.getPreviewObjectKey(),
                    documentProperties.getDownloadUrlDurationSeconds()
            ).getData();
        } catch (Exception e) {
            log.warn("document download url apply failed. resourceId={} objectKey={}",
                    resourceId, versionEntity.getPreviewObjectKey(), e);
            if (attachment) {
                throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED, e.getMessage());
            }
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_FAILED);
        }

        // 在时间戳确定之前生成附录，保证同一请求内明/暗水印时间一致
        LocalDateTime previewTime = LocalDateTime.now();

        response.setContentType("application/pdf");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "no-cache");
        if (attachment) {
            setAttachmentContentDispositionHeader(response, buildWatermarkFilename(versionEntity));
        }

        if (versionEntity.getUpdateTime() != null) {
            ZonedDateTime updateZdt = versionEntity.getUpdateTime()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC);
            String lastModifiedStr = DateTimeFormatter.RFC_1123_DATE_TIME.format(updateZdt);
            response.setHeader("Last-Modified", lastModifiedStr);

            long updateTimeMillis = updateZdt.toInstant().toEpochMilli();
            String eTag = DigestUtils.md5Hex(resourceId + "_" + versionEntity.getVersion() + "_" + updateTimeMillis);
            response.setHeader("ETag", "\"" + eTag + "\"");
        }

        try {
            String rangeHeader = request.getHeader("Range");
            if (rangeHeader == null) {
                // 全量请求：OSS 原始段 + 附录
                response.setHeader("Content-Length", String.valueOf(totalSize));
                response.setStatus(HttpStatus.OK.value());
                ServletOutputStream out = response.getOutputStream();
                pipeOssRange(ossUrl, 0, originalSize - 1, out);
                out.write(WatermarkAppendixBuilder.build(meta, userId, previewTime, documentProperties.getWatermarkSecretKey()));
            } else {
                handleRangeRequest(rangeHeader, totalSize, originalSize,
                        ossUrl, meta, userId, previewTime, response);
            }
        } catch (IOException e) {
            if (attachment) {
                log.error("document download response write failed. resourceId={}", resourceId, e);
                throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_FAILED);
            }
            log.error("document preview response write failed. resourceId={}", resourceId, e);
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_FAILED);
        } catch (Exception e) {
            if (attachment) {
                log.error("document download request handle failed. resourceId={}", resourceId, e);
                throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_FAILED);
            }
            log.error("document preview request handle failed. resourceId={}", resourceId, e);
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_FAILED);
        }
    }

    private void handleOriginalDownloadRequest(HttpServletResponse response,
                                               String resourceId,
                                               Integer targetVersion) {
        DocumentInfoEntity infoEntity = documentService.getDocumentInfo(resourceId);
        targetVersion = targetVersion != null ? targetVersion : infoEntity.getVersion();
        if (Integer.valueOf(0).equals(targetVersion)) {
            throw new ServiceException(DocumentError.DOCUMENT_HAS_NO_VERSION);
        }

        DocumentVersionEntity versionEntity = documentService.getDocumentVersion(resourceId, targetVersion);
        if (versionEntity.getDocumentStatus() == null || versionEntity.getDocumentStatus().getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_NOT_READY);
        }

        String ossUrl;
        try {
            ossUrl = remoteStorageService.getDownloadUrl(
                    versionEntity.getSourceObjectKey(),
                    documentProperties.getDownloadUrlDurationSeconds()
            ).getData();
        } catch (Exception e) {
            log.warn("document download url apply failed. resourceId={} objectKey={}",
                    resourceId, versionEntity.getSourceObjectKey(), e);
            throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED, e.getMessage());
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Cache-Control", "no-cache");
        setAttachmentContentDispositionHeader(response, buildOriginalFilename(versionEntity));

        try {
            pipeOss(ossUrl, response.getOutputStream());
        } catch (IOException e) {
            log.error("document download response write failed. resourceId={}", resourceId, e);
            throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_FAILED);
        } catch (Exception e) {
            log.error("document download request handle failed. resourceId={}", resourceId, e);
            throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_FAILED);
        }
    }

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
            byte[] appendix = WatermarkAppendixBuilder.build(meta, userId, previewTime, documentProperties.getWatermarkSecretKey());
            int offset = (int) (start - originalSize);
            int length = (int) (end - start + 1);
            out.write(appendix, offset, length);

        } else {
            // 情形 3：跨越两段边界 → OSS 尾部 + 附录头部
            pipeOssRange(ossUrl, start, originalSize - 1, out);
            byte[] appendix = WatermarkAppendixBuilder.build(meta, userId, previewTime, documentProperties.getWatermarkSecretKey());
            int lengthInAppendix = (int) (end - originalSize + 1);
            out.write(appendix, 0, lengthInAppendix);
        }
    }

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

    private void pipeOss(String ossUrl, OutputStream out) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ossUrl))
                .GET()
                .build();

        HttpResponse<InputStream> resp = HTTP_CLIENT.send(req,
                HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("OSS 下载请求失败: status=" + resp.statusCode());
        }

        byte[] buf = new byte[PIPE_BUF];
        try (InputStream in = resp.body()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    private void setAttachmentContentDispositionHeader(HttpServletResponse response, String filename) {
        String attachmentFilename = StringUtils.hasText(filename) ? filename : "document";
        attachmentFilename = attachmentFilename.replace("\r", "").replace("\n", "").trim();
        attachmentFilename = attachmentFilename.replace("/", "_").replace("\\", "_");
        if (!StringUtils.hasText(attachmentFilename)) {
            attachmentFilename = "document";
        }

        String encodedFilename = URLEncoder.encode(attachmentFilename, StandardCharsets.UTF_8).replace("+", "%20");
        String asciiFallback = attachmentFilename
                .replace("\"", "'")
                .replaceAll("[^\\x20-\\x7E]", "_");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encodedFilename);
    }

    private String buildOriginalFilename(DocumentVersionEntity versionEntity) {
        DocumentUploadMeta uploadMeta = versionEntity.getUploadMeta();
        String documentName = uploadMeta != null ? uploadMeta.getDocumentName() : null;
        String extension = uploadMeta != null && uploadMeta.getFileType() != null ? uploadMeta.getFileType().getExtension() : null;
        String resolvedName = StringUtils.hasText(documentName) ? documentName : "document";
        if (!StringUtils.hasText(extension)) {
            return resolvedName;
        }
        String suffix = "." + extension;
        return resolvedName.toLowerCase().endsWith(suffix.toLowerCase()) ? resolvedName : resolvedName + suffix;
    }

    private String buildWatermarkFilename(DocumentVersionEntity versionEntity) {
        DocumentUploadMeta uploadMeta = versionEntity.getUploadMeta();
        String documentName = uploadMeta != null ? uploadMeta.getDocumentName() : null;
        String baseName;
        if (StringUtils.hasText(documentName)) {
            String result;
            int slashIndex = Math.max(documentName.lastIndexOf('/'), documentName.lastIndexOf('\\'));
            int dotIndex = documentName.lastIndexOf('.');
            if (dotIndex > slashIndex) {
                result = documentName.substring(0, dotIndex);
            } else {
                result = documentName;
            }
            baseName = result;
        } else {
            baseName = "document";
        }
        return baseName + "-watermark.pdf";
    }

}
