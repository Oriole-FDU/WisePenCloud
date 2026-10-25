package com.oriole.wisepen.resource.domain.dto.res;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FavoriteItemResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "资源信息；资源不可访问时为 null")
    private ResourceItemResponse resourceInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "收藏时间")
    private LocalDateTime favoritedAt;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "所属收藏夹 ID 列表")
    private List<String> collectionIds = new ArrayList<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源当前是否可访问")
    private Boolean accessible;
}
