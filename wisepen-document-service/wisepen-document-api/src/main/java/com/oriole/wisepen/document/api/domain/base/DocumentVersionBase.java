package com.oriole.wisepen.document.api.domain.base;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "上传元信息")
    private DocumentUploadMeta uploadMeta;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文档处理状态")
    private DocumentStatus documentStatus;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "最大预览页数")
    private Integer maxPreviewPages;
}
