package com.oriole.wisepen.note.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteSearchTextResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "笔记版本号")
    private Integer version;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "搜索文本")
    private String searchText;
}
