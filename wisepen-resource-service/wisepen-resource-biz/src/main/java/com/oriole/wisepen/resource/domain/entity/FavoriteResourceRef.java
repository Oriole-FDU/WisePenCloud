package com.oriole.wisepen.resource.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_favorite_resource_refs")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_user_resource", def = "{'userId': 1, 'resourceId': 1}", unique = true),
        @CompoundIndex(name = "idx_user_favorited", def = "{'userId': 1, 'favoritedAt': -1}"),
        @CompoundIndex(name = "idx_user_collection_favorited", def = "{'userId': 1, 'collectionIds': 1, 'favoritedAt': -1}")
})
public class FavoriteResourceRef {
    @Id
    private String id;

    private String userId;

    @Indexed
    private String resourceId;

    private List<String> collectionIds = new ArrayList<>();

    @CreatedDate
    @LastModifiedDate
    private LocalDateTime favoritedAt;
}
