package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteCollectionBase {
    private String collectionName;
    private String description;
    private Boolean isDefault;
}
