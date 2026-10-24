package com.oriole.wisepen.file.storage.api.domain.base;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StorageRecordBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文件的相对路径")
    private String objectKey; // 文件的相对路径

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "文件物理 MD5")
    private String md5; // 文件物理 MD5

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "string", format = "int64", description = "物理字节大小")
    private Long size; // 物理字节大小
}
