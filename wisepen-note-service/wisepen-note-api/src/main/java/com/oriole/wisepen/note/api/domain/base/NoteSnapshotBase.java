package com.oriole.wisepen.note.api.domain.base;

import com.oriole.wisepen.note.api.domain.enums.VersionType;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class NoteSnapshotBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "版本号")
    private Integer version;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "版本类型")
    private VersionType type;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "版本数据")
    private String data;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "用于搜索的纯文本")
    private String plainText;
}
