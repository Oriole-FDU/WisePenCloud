package com.oriole.wisepen.note.api.domain.dto.res;

import com.oriole.wisepen.note.api.domain.base.NoteVersionBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

@EqualsAndHashCode(callSuper = true)
@Data
public class NoteVersionInfoResponse extends NoteVersionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "版本号")
    private Integer version;
}
