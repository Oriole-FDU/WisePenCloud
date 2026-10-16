package com.oriole.wisepen.file.storage.api.domain.dto;

import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopyReqDTO implements Serializable {

    @NotBlank(message = "源文件 ObjectKey 不能为空")
    private String sourceObjectKey;

    @NotNull(message = "存储场景不能为空")
    private StorageSceneEnum scene;

    private String bizTag;

    /** 仅 PRIVATE_DOC_ASSET 使用固定名称，其余场景保持随机名称。 */
    private String targetFileName;
}
