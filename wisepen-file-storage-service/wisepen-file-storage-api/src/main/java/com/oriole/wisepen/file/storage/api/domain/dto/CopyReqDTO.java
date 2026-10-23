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

    /** 可选固定目标文件名；未传时沿用现有随机命名。 */
    private String targetFileName;
}
