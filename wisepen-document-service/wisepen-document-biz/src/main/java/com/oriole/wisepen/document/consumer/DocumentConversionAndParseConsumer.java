package com.oriole.wisepen.document.consumer;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.constant.DocumentConstants;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.document.service.IDocumentFileService;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.document.util.MarkdownPageBreakInjector;
import com.oriole.wisepen.document.util.OnlyOfficeConversionClient;
import com.oriole.wisepen.document.util.WatermarkPreProcessor;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.enums.ResourceType;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_PARSE;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConversionAndParseConsumer {

    /** 复用单例 HttpClient，用于下载源文件和上传 PDF 预览至 OSS 预签名 URL */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final RemoteStorageService remoteStorageService;
    private final IDocumentFileService documentFileService;
    private final IDocumentService documentService;

    private final DocumentProperties documentProperties;
    private final WatermarkPreProcessor watermarkPreProcessor;
    private final MarkdownPageBreakInjector markdownPageBreakInjector;

    @KafkaListener(topics = TOPIC_DOCUMENT_PARSE, groupId = "wisepen-document-parse-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_DOCUMENT_PARSE,
            description = "消费文档解析任务，下载源文件并完成格式转换、内容抽取、预览 PDF 上传和文档状态推进。",
            payloadType = DocumentParseTaskMessage.class,
            message = @AsyncMessage(name = "DocumentParseTaskMessage", title = "文档解析任务")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-document-parse-group")
    public void onDocumentParse(DocumentParseTaskMessage msg) {
        log.info("document parse event received. topic={} documentId={}",
                TOPIC_DOCUMENT_PARSE, msg.getDocumentId());
        try {
            process(msg);
            log.debug("document parse event consumed. topic={} documentId={}",
                    TOPIC_DOCUMENT_PARSE, msg.getDocumentId());
        } catch (Exception e) {
            log.error("document parse event consumption failed. topic={} documentId={}",
                    TOPIC_DOCUMENT_PARSE, msg.getDocumentId(), e);
            documentService.updateStatus(msg.getDocumentId(), new DocumentStatus(e.getMessage()));
        }
    }

    private void process(DocumentParseTaskMessage msg) throws IOException, InterruptedException {

        DocumentStatus status = documentService.getDocumentStatus(msg.getDocumentId()).orElse(null);
        if (status == null) {
            log.info("document parse skipped because version is missing. documentId={}", msg.getDocumentId());
            return;
        }

        if (status.getStatus() != DocumentStatusEnum.UPLOADED) {
            log.info("document parse skipped because status mismatched. documentId={} status={}",
                    msg.getDocumentId(), status.getStatus());
            return;
        }

        // 将文档状态推进至 CONVERTING_AND_PARSING
        documentService.updateStatus(msg.getDocumentId(), new DocumentStatus(DocumentStatusEnum.CONVERTING_AND_PARSING));

        // 获取内网下载 URL，将源文件下载到本地临时目录
        String downloadUrl = remoteStorageService.getDownloadUrl(msg.getSourceObjectKey(), null, null).getData();
        String ext = msg.getFileType().getExtension();
        File sourceFile = downloadSourceFile(downloadUrl, msg.getDocumentId(), ext);

        // Office 文件经转换为 PDF；PDF 文件直接使用
        boolean isOffice = DocumentConstants.OFFICE_TYPES.contains(msg.getFileType());
        File pdfFile = isOffice ? createCacheFile(msg.getDocumentId(), ".pdf") : sourceFile;
        File hookedPdf = createCacheFile(msg.getDocumentId(), "_hook.pdf");
        File mdFile = createCacheFile(msg.getDocumentId(), ".md");

        boolean isOnlyOfficeAvailable = "onlyoffice".equalsIgnoreCase(documentProperties.getConversionProvider());
        try {
            // Office → PDF 格式转换
            if (isOffice) {
                if (isOnlyOfficeAvailable) {
                    // 基于 OnlyOffice 转换为 PDF
                    documentFileService.convertTo(downloadUrl, sourceFile.getName(), msg.getFileType(), pdfFile, OnlyOfficeConversionClient.ConversionTargetType.PDF);
                } else {
                    // 基于 Jodconverter 转换为 PDF
                    documentFileService.convertTo(sourceFile, pdfFile);
                }
            }

            DocumentContentEntity content;
            // 如果文件是 PDF
            if (ResourceType.PDF == msg.getFileType()) {
                 // 使用 PDFBox 转为 Text
                String rawText = documentFileService.extractPDFText(pdfFile);
                content = DocumentContentEntity.builder().rawText(rawText).build();
            } else if (Set.of(ResourceType.DOC, ResourceType.DOCX).contains(msg.getFileType())) {
                // 如果文件是 DOC/DOCX
                if (isOnlyOfficeAvailable) { // 如果 OnlyOffice 可用，使用 OnlyOffice 转为 MD
                    documentFileService.convertTo(downloadUrl, sourceFile.getName(), msg.getFileType(), mdFile, OnlyOfficeConversionClient.ConversionTargetType.MD);
                    byte[] bytes = Files.readAllBytes(mdFile.toPath());
                    content = DocumentContentEntity.builder().markdown(new String(bytes, StandardCharsets.UTF_8)).build();
                } else { // 如果 OnlyOffice 不可用，使用 POI 转为 MD
                    String markdown = documentFileService.extractMarkdown(sourceFile, msg.getFileType());
                    content = DocumentContentEntity.builder().markdown(markdown).build();
                }
            } else { // 如果文件是 PPT/PPTX/XLS/XLSX，使用 POI 转为 MD
                String markdown = documentFileService.extractMarkdown(sourceFile, msg.getFileType());
                content = DocumentContentEntity.builder().markdown(markdown).build();
            }

            if (ResourceType.DOC == msg.getFileType() || ResourceType.DOCX == msg.getFileType() && StrUtil.isNotBlank(content.getMarkdown())) {
                content.setMarkdown(markdownPageBreakInjector.inject(content.getMarkdown(), pdfFile, msg.getDocumentId()));
            }

            // 预埋空水印占位 Form XObject（/WisepenWM），生成 hooked PDF（预览PDF）
            // 上传至 OSS 的是 hooked PDF，而非原始 pdfFile
            DocumentPdfMetaEntity pdfMeta = watermarkPreProcessor.processAndExtractMeta(pdfFile, hookedPdf);
            // 将 hooked PDF 上传至 OSS
            String previewKey = uploadPreviewPdf(msg.getDocumentId(), hookedPdf);

            documentService.saveConversionAndParseResult(msg.getDocumentId(), previewKey, pdfMeta, content);
            documentService.finalizeToReady(msg.getDocumentId());
            log.info("document parse finished. documentId={} previewObjectKey={}",
                    msg.getDocumentId(), previewKey);

        } finally {
            deleteSilently(sourceFile);
            if (isOffice) deleteSilently(pdfFile);
            deleteSilently(hookedPdf);
            deleteSilently(mdFile);
        }
    }

    /** 流式下载源文件到本地缓存目录 */
    private File downloadSourceFile(String url, String documentId, String ext) throws IOException, InterruptedException {
        Path dir = Paths.get(documentProperties.getCachePath());
        Files.createDirectories(dir);
        Path target = dir.resolve(documentId + "_source." + ext);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(target));
        return target.toFile();
    }

    /** 向 storage 服务申请 PDF 预览文件的预签名直传 URL，然后通过 HTTP PUT 将 PDF 上传至 OSS */
    private String uploadPreviewPdf(String documentId, File pdfFile) throws IOException, InterruptedException {
        UploadInitRespDTO uploadInitRespDTO;
        try {
            uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .extension("pdf")
                    .scene(StorageSceneEnum.PRIVATE_DOC)
                    .bizTag(documentId)
                    .isNeedCallback(false)
                    .build()).getData();
        }
        catch (Exception e) {
            log.warn("preview upload init failed. documentId={} dependency=storageService",
                    documentId, e);
            throw new ServiceException(DocumentError.DOCUMENT_UPLOAD_URL_APPLY_FAILED, e.getMessage());
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(uploadInitRespDTO.getPutUrl()))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(pdfFile.toPath()));
        if (StrUtil.isNotBlank(uploadInitRespDTO.getCallbackHeader())) {
            reqBuilder.header("x-oss-callback", uploadInitRespDTO.getCallbackHeader());
        }
        HttpResponse<Void> resp = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("PDF 上传至 OSS 失败 StatusCode=" + resp.statusCode());
        }
        return uploadInitRespDTO.getObjectKey();
    }

    /** 在缓存目录下创建临时文件（用于存放 Office→PDF 转换产物） */
    private File createCacheFile(String documentId, String suffix) throws IOException {
        Path dir = Paths.get(documentProperties.getCachePath());
        Files.createDirectories(dir);
        return Files.createTempFile(dir, documentId + "_", suffix).toFile();
    }

    /** 静默删除本地临时文件，失败时仅打印警告，不影响主流程。 */
    private void deleteSilently(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception e) {
                log.warn("cache file delete failed. path={}", file.getAbsolutePath(), e);
            }
        }
    }
}
