package com.oriole.wisepen.document.api.domain.base;

import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatus {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文档处理状态")
    private DocumentStatusEnum status;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "失败原因")
    private String errorMessage;

    public DocumentStatus(DocumentStatusEnum status) {
        this.status = status;
    }

    public DocumentStatus(String errorMessage) {
        this.status = DocumentStatusEnum.FAILED;
        this.errorMessage = errorMessage;
    }
}
