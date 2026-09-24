package com.oriole.wisepen.questionnaire.repository;

import com.oriole.wisepen.questionnaire.domain.entity.TableVersionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableVersionRepository extends MongoRepository<TableVersionEntity, String> {
    Optional<TableVersionEntity> findByResourceIdAndVersion(String resourceId, Integer version);

    List<TableVersionEntity> findByResourceId(String resourceId);

    /** 删除指定资源的所有问卷版本记录 */
    long deleteByResourceId(String resourceId);

    void deleteByResourceIdIn(List<String> resourceIds);
}
