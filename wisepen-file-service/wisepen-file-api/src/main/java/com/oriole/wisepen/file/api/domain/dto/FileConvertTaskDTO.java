package com.oriole.wisepen.file.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import java.io.Serial;

import lombok.EqualsAndHashCode;

/**
 * 文件转换任务 DTO
 *
 * @author Ian.xiong
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FileConvertTaskDTO extends FileTaskBase {
    @Serial
    private static final long serialVersionUID = 1L;

    // 文件扩展名
    private String extension;
    
    // 原始文件大小
    private Long originalSize;
}
