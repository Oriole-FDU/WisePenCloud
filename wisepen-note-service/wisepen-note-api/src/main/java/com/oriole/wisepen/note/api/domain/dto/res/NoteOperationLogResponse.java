package com.oriole.wisepen.note.api.domain.dto.res;

import com.oriole.wisepen.note.api.domain.base.NoteOperationLogBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

@EqualsAndHashCode(callSuper = true)
@Data
public class NoteOperationLogResponse extends NoteOperationLogBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "操作日志 ID")
    private String id;
}
