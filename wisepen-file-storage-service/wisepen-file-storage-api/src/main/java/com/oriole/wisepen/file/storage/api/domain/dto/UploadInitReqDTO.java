package com.oriole.wisepen.file.storage.api.domain.dto;

import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitReqDTO implements Serializable {
    private String md5;
    private String extension;
    private StorageSceneEnum scene;
    private String bizTag;
    /**
     * 内部服务生成固定文件名产物时指定目标 objectKey。
     * 为空时由存储服务按 scene/bizTag/extension 自动生成。
     */
    private String targetObjectKey;
    private Long configId;
    private Long expectedSize;
    private Boolean isNeedCallback = true;
}
