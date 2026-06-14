package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.base.FavoriteCollectionBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_favorite_collections")
@CompoundIndexes({
    @CompoundIndex(def = "{'userId': 1}"),
    @CompoundIndex(def = "{'userId': 1, 'isDefault': 1}")
})
public class FavoriteCollectionEntity extends FavoriteCollectionBase {
    @Id
    private String collectionId;

    private String userId;

    @Builder.Default
    private Integer itemCount = 0;

    @CreatedDate
    private LocalDateTime createTime;

    public FavoriteCollectionEntity(String userId, String collectionName, String description, Boolean isDefault) {
        super(collectionName, description, isDefault);
        this.userId = userId;
    }
}
