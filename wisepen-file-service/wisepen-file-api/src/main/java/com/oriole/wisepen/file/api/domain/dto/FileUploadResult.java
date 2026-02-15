package com.oriole.wisepen.file.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件上传响应结果 (仅包含必要信息)
 *
 * @author Ian.Xiong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 文件名 */
    private String filename;
}
