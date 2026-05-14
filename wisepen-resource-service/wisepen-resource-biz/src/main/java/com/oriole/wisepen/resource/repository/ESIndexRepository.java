package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * ES 搜索索引仓储接口，复杂查询由 SearchQueryServiceImpl 直接使用
 * {@link org.springframework.data.elasticsearch.core.ElasticsearchOperations} 完成。
 */
@Repository
public interface ESIndexRepository extends ElasticsearchRepository<ESIndexEntity, String> {
}
