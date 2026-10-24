package com.oriole.wisepen.resource.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceFavoriteStatusResponse {
    /** 该资源所归属的所有收藏集合 ID 列表；未收藏时为空列表，不会为 null；列表非空即表示已收藏 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "所属收藏夹 ID 列表；未收藏时为空数组")
    private List<String> collectionIds = new ArrayList<>();
}
