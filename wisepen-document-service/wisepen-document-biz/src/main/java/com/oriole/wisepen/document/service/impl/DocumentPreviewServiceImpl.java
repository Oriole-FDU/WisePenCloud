package com.oriole.wisepen.document.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
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
import com.oriole.wisepen.resource.domain.dto.ResourceItemInfoResDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.oriole.wisepen.document.exception.DocumentError.DOCUMENT_NOT_FOUND;
import static org.apache.poi.xdgf.util.Util.sanitizeFilename;

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

    private final RemoteResourceService remoteResourceService;

    @Override
    public void handlePreviewRequest(HttpServletRequest request,
                                     HttpServletResponse response,
                                     String resourceId,
                                     Integer targetVersion,
                                     String userId) {
        handleWatermarkPdfRequest(request, response, resourceId, targetVersion, userId, null, false);
    }

    @Override
    public void handleDownloadRequest(HttpServletRequest request,
                                      HttpServletResponse response,
                                      String resourceId,
                                      Integer targetVersion,
                                      String userId,
                                      DocumentDownloadType downloadType) {

        List<ResourceItemInfoResDTO> resourceInfos = remoteResourceService.listResourceBaseInfo(List.of(resourceId)).getData();
        ResourceItemInfoResDTO resourceInfo = (resourceInfos == null ? List.<ResourceItemInfoResDTO>of() : resourceInfos)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ServiceException(DOCUMENT_NOT_FOUND));

        String resourceName = resourceInfo.getResourceName();
        ResourceType resourceType = resourceInfo.getResourceType();
        String filename = StringUtils.hasText(resourceName) ? resourceName.trim() : "document";

        if (DocumentDownloadType.ORIGINAL == downloadType) {
            String extension = resourceType == null ? null : resourceType.getExtension();
            if (StringUtils.hasText(extension)) filename = filename + "." + extension;

            handleOriginalDownloadRequest(response, resourceId, targetVersion, filename);
        } else {
            filename = filename + "-watermark.pdf";
            handleWatermarkPdfRequest(request, response, resourceId, targetVersion, userId, filename, true);
        }
    }

    private void handleWatermarkPdfRequest(HttpServletRequest request,
                                           HttpServletResponse response,
                                           String resourceId,
                                           Integer targetVersion,
                                           String userId,
                                           String downloadFilename,
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

        String contentDisposition = attachment ? ContentDisposition.attachment()
                .filename(sanitizeFilename(downloadFilename), StandardCharsets.UTF_8)
                .build().toString() : null;
        try {
            ossUrl = remoteStorageService.getDownloadUrl(
                    versionEntity.getPreviewObjectKey(), documentProperties.getDownloadUrlDurationSeconds(), contentDisposition
            ).getData();
        } catch (Exception e) {
            log.warn("document {} url apply failed. resourceId={} objectKey={}",
                    attachment ? "download" : "preview", resourceId, versionEntity.getPreviewObjectKey(), e);
            throw new ServiceException(attachment ? DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED :
                    DocumentError.DOCUMENT_PREVIEW_FAILED);
        }

        // 在时间戳确定之前生成附录，保证同一请求内明/暗水印时间一致
        LocalDateTime previewTime = LocalDateTime.now();

        response.setContentType("application/pdf");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "no-cache");
        if (attachment) {
            response.setHeader("Content-Disposition", contentDisposition);
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
            log.error("document {} response write failed. resourceId={}",
                    attachment ? "download" : "preview", resourceId, e);
            throw new ServiceException(attachment ? DocumentError.DOCUMENT_DOWNLOAD_FAILED :
                    DocumentError.DOCUMENT_PREVIEW_FAILED);
        } catch (Exception e) {
            log.error("document {} request handle failed. resourceId={}",
                    attachment ? "download" : "preview", resourceId, e);
            throw new ServiceException(attachment ? DocumentError.DOCUMENT_DOWNLOAD_FAILED :
                    DocumentError.DOCUMENT_PREVIEW_FAILED);
        }
    }

    private void handleOriginalDownloadRequest(HttpServletResponse response,
                                               String resourceId,
                                               Integer targetVersion,
                                               String downloadFilename) {
        DocumentInfoEntity infoEntity = documentService.getDocumentInfo(resourceId);
        targetVersion = targetVersion != null ? targetVersion : infoEntity.getVersion();
        if (Integer.valueOf(0).equals(targetVersion)) {
            throw new ServiceException(DocumentError.DOCUMENT_HAS_NO_VERSION);
        }

        DocumentVersionEntity versionEntity = documentService.getDocumentVersion(resourceId, targetVersion);
        if (versionEntity.getDocumentStatus() == null
                || versionEntity.getDocumentStatus().getStatus() != DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentError.DOCUMENT_PREVIEW_NOT_READY);
        }
        String ossUrl;
        String contentDisposition = ContentDisposition.attachment()
                .filename(sanitizeFilename(downloadFilename), StandardCharsets.UTF_8).build().toString();
        try {
            ossUrl = remoteStorageService.getDownloadUrl(
                    versionEntity.getSourceObjectKey(),
                    documentProperties.getDownloadUrlDurationSeconds(),
                    contentDisposition
            ).getData();
        } catch (Exception e) {
            log.warn("document download url apply failed. resourceId={} objectKey={}",
                    resourceId, versionEntity.getSourceObjectKey(), e);
            throw new ServiceException(DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED, e.getMessage());
        }

        // 原始文件不需要经过文档服务，鉴权后将浏览器重定向到 OSS 限时签名地址，
        // 由对象存储直接承担文件下载流量。
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Location", ossUrl);
        response.setStatus(HttpStatus.FOUND.value());
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
}
