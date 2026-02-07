package com.oriole.wisepen.file.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件转换任务 DTO
 *
 * @author Ian.Xiong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileConvertTaskDTO implements Serializable {
    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件扩展名
     */
    private String extension;

    /**
     * 临时文件存储路径
     */
    private String tempFilePath;
    
    /**
     * 原始文件大小
     */
    private Long originalSize;
    
    /**
     * md5
     */
    private String md5;
}
