package com.oriole.wisepen.file.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreUploadResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否已存在（可秒传）
     */
    private boolean available;

    /**
     * 秒传成功后的文件ID (仅 available 为 true 时有效)
     */
    private Long fileId;

    /**
     * 是否允许上传（校验体积、空间等）
     */
    private boolean canUpload;

    /**
     * 如果不允许上传，提供原因
     */
    private String reason;
}
