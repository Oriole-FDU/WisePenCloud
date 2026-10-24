package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.FavoriteCollectionBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class FavoriteCollectionResponse extends FavoriteCollectionBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "收藏夹 ID")
    private String collectionId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int32", description = "收藏条目数")
    private Integer itemCount;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;
}
