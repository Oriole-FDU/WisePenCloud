package com.oriole.wisepen.document.util;

import cn.hutool.core.util.IdUtil;
import com.onlyoffice.client.ApacheHttpclientDocumentServerClient;
import com.onlyoffice.client.DocumentServerClient;
import com.onlyoffice.client.DocumentServerClientSettings;
import com.onlyoffice.model.convertservice.ConvertRequest;
import com.onlyoffice.model.convertservice.ConvertResponse;
import com.onlyoffice.model.settings.security.Security;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;

@Slf4j
@Component
public class OnlyOfficeConversionClient {

    private final DocumentProperties documentProperties;
    private final DocumentServerClient documentServerClient;

    public OnlyOfficeConversionClient(DocumentProperties documentProperties) {
        this.documentProperties = documentProperties;
        this.documentServerClient = new ApacheHttpclientDocumentServerClient(
                DocumentServerClientSettings.builder()
                        .baseUrl(documentProperties.getIntegration().getOnlyoffice().getInternalUrl())
                        .security(Security.builder()
                                .key(documentProperties.getIntegration().getOnlyoffice().getJwtSecret())
                                .header(documentProperties.getIntegration().getOnlyoffice().getJwtHeader())
                                .prefix(documentProperties.getIntegration().getOnlyoffice().getJwtPrefix())
                                .build())
                        .ignoreSSLCertificate(false)
                        .build()
        );
    }

    @Getter
    @AllArgsConstructor
    public enum ConversionTargetType {
        PDF("pdf"),
        MD("md");

        private final String extension;
    }

    public void convert(String sourceUrl, ResourceType fileType, ConversionTargetType target, File outputFile) {
        String key = IdUtil.fastSimpleUUID();

        ConvertRequest convertReq = ConvertRequest.builder()
                .async(true) // 使用异步转换
                .filetype(fileType.getExtension()) // 源文件扩展名
                .outputtype(target.getExtension()) // 目标格式
                .key(key) // 转换任务标识，轮询时必须保持一致
                .title(key + "." + target.getExtension()) // 文件标题
                .url(sourceUrl)
                .build();

        // 计算转换超时截止时间
        long deadline = System.currentTimeMillis() + documentProperties.getConversionRuntime().getTimeoutMs();
        ConvertResponse convertRes = postConvert(convertReq); // 调用

        while (!Boolean.TRUE.equals(convertRes.getEndConvert())) { // 只要 endConvert 不是 true，就继续
            if (convertRes.getError() != null) {
                throw new ServiceException(DocumentError.DOCUMENT_PROCESS_CONVERT_FAILED, convertRes.getError().getDescription());
            }
            // 超时报错
            if (System.currentTimeMillis() >= deadline) {
                throw new ServiceException(DocumentError.DOCUMENT_PROCESS_CONVERT_FAILED, "ONLYOFFICE conversion timeout");
            }
            // 等待
            try {
                Thread.sleep(documentProperties.getConversionRuntime().getPollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException(DocumentError.DOCUMENT_PROCESS_CONVERT_FAILED, e.getMessage());
            }
            // 再次查询
            convertRes = postConvert(convertReq);
        }
        if (convertRes.getError() != null) {
            throw new ServiceException(DocumentError.DOCUMENT_PROCESS_CONVERT_FAILED, convertRes.getError().getDescription());
        }
        downloadResult(convertRes.getFileUrl(), outputFile);
    }

    private ConvertResponse postConvert(ConvertRequest convertRequest) {
        try {
            return documentServerClient.convert(convertRequest);
        } catch (Exception e) {
            log.error("onlyoffice conversion request failed. key={}", convertRequest.getKey(), e);
            throw new ServiceException(DocumentError.DOCUMENT_PROCESS_CONVERT_FAILED, e.getMessage());
        }
    }

    private void downloadResult(String fileUrl, File outputFile) {
        try {
            try (OutputStream outputStream = Files.newOutputStream(outputFile.toPath())) {
                documentServerClient.getFile(fileUrl, outputStream);
            }
        } catch (Exception e) {
            log.error("onlyoffice conversion result download failed. outputFile={}", outputFile.getName(), e);
            throw new ServiceException(DocumentError.DOCUMENT_PROCESS_CONVERT_FAILED, e.getMessage());
        }
    }
}
