package com.oriole.wisepen.document.api.domain.dto.res;

import com.oriole.wisepen.document.api.domain.base.DocumentVersionBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DocumentVersionInfoResponse extends DocumentVersionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "文档版本号")
    private Integer version;
}
