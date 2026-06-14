package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.FavoriteCollectionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FavoriteCollectionRepository extends MongoRepository<FavoriteCollectionEntity, String> {

    List<FavoriteCollectionEntity> findByUserIdOrderByIsDefaultDescCreateTimeDesc(String userId);

    Optional<FavoriteCollectionEntity> findFirstByCollectionIdAndUserId(String collectionId, String userId);

    Optional<FavoriteCollectionEntity> findFirstByUserIdAndIsDefaultTrue(String userId);

    List<FavoriteCollectionEntity> findByCollectionIdInAndUserId(Collection<String> collectionIds, String userId);
}
