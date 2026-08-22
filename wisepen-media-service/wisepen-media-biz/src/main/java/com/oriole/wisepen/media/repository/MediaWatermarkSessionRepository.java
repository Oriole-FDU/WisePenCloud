package com.oriole.wisepen.media.repository;

import com.oriole.wisepen.media.domain.entity.MediaWatermarkSessionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaWatermarkSessionRepository extends MongoRepository<MediaWatermarkSessionEntity, String> {

    List<MediaWatermarkSessionEntity> findByResourceIdIn(List<String> resourceIds);

    void deleteByResourceIdIn(List<String> resourceIds);
}
