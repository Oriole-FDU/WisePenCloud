package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteCollectionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "收藏夹名称")
    private String collectionName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "收藏夹描述")
    private String description;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否为默认收藏夹")
    private Boolean isDefault;
}
