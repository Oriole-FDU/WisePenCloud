package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.FavoriteResourceRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FavoriteResourceRefRepository extends MongoRepository<FavoriteResourceRef, String> {

    Optional<FavoriteResourceRef> findFirstByUserIdAndResourceId(String userId, String resourceId);

    Page<FavoriteResourceRef> findByUserId(String userId, Pageable pageable);

    @Query("{ 'userId': ?0, 'collectionIds': ?1 }")
    Page<FavoriteResourceRef> findByUserIdAndCollectionId(String userId, String collectionId, Pageable pageable);

    @Query("{ 'userId': ?0, 'collectionIds': ?1 }")
    List<FavoriteResourceRef> findByUserIdAndCollectionId(String userId, String collectionId);

    @Query(value = "{ 'userId': ?0, 'collectionIds': ?1 }", count = true)
    long countByUserIdAndCollectionId(String userId, String collectionId);

    List<FavoriteResourceRef> findByResourceIdIn(Collection<String> resourceIds);

    void deleteByResourceIdIn(Collection<String> resourceIds);
}
