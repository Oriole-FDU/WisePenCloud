package com.oriole.wisepen.resource.domain.dto.res;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FavoriteItemResponse {
    private ResourceItemResponse resourceInfo;
    private LocalDateTime favoritedAt;
    private List<String> collectionIds;
    private Boolean accessible;
}
