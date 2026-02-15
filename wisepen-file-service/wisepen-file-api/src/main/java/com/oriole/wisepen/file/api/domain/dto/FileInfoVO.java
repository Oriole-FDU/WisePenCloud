package com.oriole.wisepen.file.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息展示 VO（对齐需求文档 Output 字段命名）
 *
 * @author Ian.Xiong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long documentId;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 上传时间 */
    private LocalDateTime createTime;

    /** 状态：0=处理中, 1=可用, 2=失败 */
    private Integer status;
}
