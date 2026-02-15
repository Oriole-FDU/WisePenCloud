package com.oriole.wisepen.file.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件上传任务 DTO
 * 
 * @author Ian.Xiong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadTaskDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 临时文件存储路径 (服务器本地缓存路径)
     */
    private String tempFilePath;

    /**
     * 目标存储路径 (模拟 OSS 的最终路径)
     */
    private String targetPath;

    /**
     * MD5
     */
    private String md5;

    /**
     * 是否为转换后的 PDF 副本
     */
    private Boolean isConvertedPdf;

    /**
     * 是否为 PDF 直传（原文件即 PDF，url 和 pdfUrl 写同一地址）
     */
    private Boolean isPdfDirect;

    /**
     * Web 访问 URL (可选，若存在则优先使用此值更新 DB，而非 targetPath)
     */
    private String accessUrl;
}
