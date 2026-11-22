package com.oriole.wisepen.media.repository;

import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaInfoRepository extends MongoRepository<MediaInfoEntity, String> {

    Optional<MediaInfoEntity> findByResourceId(String resourceId);

    List<MediaInfoEntity> findByResourceIdIn(List<String> resourceIds);

    void deleteByResourceIdIn(List<String> resourceIds);
}
