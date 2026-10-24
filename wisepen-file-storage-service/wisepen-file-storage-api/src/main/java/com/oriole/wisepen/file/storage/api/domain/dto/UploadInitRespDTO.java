package com.oriole.wisepen.file.storage.api.domain.dto;

import com.oriole.wisepen.file.storage.api.domain.base.UploadUrlBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitRespDTO extends UploadUrlBase implements Serializable {
    /** 是否触发秒传 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否触发秒传")
    private Boolean flashUploaded;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文件访问域名")
    private String domain;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文件相对路径")
    private String objectKey;
}
