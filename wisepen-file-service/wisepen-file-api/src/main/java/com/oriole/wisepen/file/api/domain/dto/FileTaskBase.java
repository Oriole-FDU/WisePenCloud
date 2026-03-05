package com.oriole.wisepen.file.api.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.io.Serializable;

/**
 * 异步文件处理任务的基础类，抽取了所有子任务共用的基础字段。
 *
 * @author Ian.Xiong
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class FileTaskBase implements Serializable {
    private static final long serialVersionUID = 1L;

    // 文件ID
    private Long fileId;

    // 原始文件名
    private String originalFilename;

    // MD5
    private String md5;

    // 临时文件存储路径 (服务器本地缓存路径)
    private String tempFilePath;
}
