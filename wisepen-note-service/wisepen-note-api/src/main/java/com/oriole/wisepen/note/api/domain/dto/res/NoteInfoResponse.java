package com.oriole.wisepen.note.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NoteInfoResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源信息")
    private ResourceItemResponse resourceInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "当前笔记版本号")
    private Integer version;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "作者展示信息；查询失败时为空 Map")
    @Builder.Default
    private Map<Long, UserDisplayBase> authorsDisplay = new HashMap<>();
}
