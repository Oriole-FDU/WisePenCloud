package com.oriole.wisepen.media.repository;

import com.oriole.wisepen.media.domain.entity.MediaMetaEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaMetaRepository extends MongoRepository<MediaMetaEntity, String> {
}
