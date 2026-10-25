package com.oriole.wisepen.note.api.domain.base;

import com.oriole.wisepen.note.api.domain.enums.VersionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NoteVersionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "版本类型")
    private VersionType type;

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "该版本的作者用户 ID 列表")
    private List<Long> createdBy = new ArrayList<>();
}
