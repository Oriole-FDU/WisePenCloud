package com.oriole.wisepen.note.api.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NoteOperationLogBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "操作用户 ID")
    private String userId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "操作类型")
    private String operationType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "操作内容摘要")
    private String contentSummary;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "操作时间")
    private LocalDateTime timestamp;
    /** 合并的原子操作数（颗粒度合并时 >1） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "合并的原子操作数")
    private Integer mergedCount;
    /** BlockNote 树状突变详情 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "操作详情")
    private Object details;
}
