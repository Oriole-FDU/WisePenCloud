package com.oriole.wisepen.file.api.domain.dto;

import com.oriole.wisepen.file.api.domain.base.FileTaskBase;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import lombok.EqualsAndHashCode;

/**
 * 文件上传任务 DTO
 * 
 * @author Ian.xiong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadTaskDTO extends FileTaskBase {
    @Serial
    private static final long serialVersionUID = 1L;

    // 目标存储路径 (模拟 OSS 的最终路径)
    private String targetPath;

    // 是否为转换后的 PDF 副本
    private Boolean isConvertedPdf;

    // 是否为 PDF 直传（原文件即 PDF，url 和 pdfUrl 写同一地址）
    private Boolean isPdfDirect;

    // Web 访问 URL (可选，若存在则优先使用此值更新 DB，而非 targetPath)
    private String accessUrl;
}
