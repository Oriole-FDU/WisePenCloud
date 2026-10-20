package com.oriole.wisepen.document.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.onlyoffice.model.documenteditor.Config;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeEditorConfigResponse implements Serializable {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "编辑器会话 ID")
    private String sessionId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OnlyOffice 编辑器配置")
    private Config config;
}
