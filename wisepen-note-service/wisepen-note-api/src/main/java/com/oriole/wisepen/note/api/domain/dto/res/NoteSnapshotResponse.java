package com.oriole.wisepen.note.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteSnapshotResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "当前版本号")
    private Integer version;
    /** Base64 编码的最近一次 FULL 快照 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "最近一次 FULL 快照")
    private String fullSnapshot;
    /** 最近 FULL 之后的 DELTA 增量链（Base64 数组），用于崩溃恢复 */
    @Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "最近 FULL 之后的 DELTA 增量链；无增量时为空数组")
    private List<String> deltas = new ArrayList<>();
}
