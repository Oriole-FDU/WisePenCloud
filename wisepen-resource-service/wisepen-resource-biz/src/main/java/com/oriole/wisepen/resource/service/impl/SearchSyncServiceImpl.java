package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.repository.ESIndexRepository;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import com.oriole.wisepen.resource.enums.UpsertField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncServiceImpl implements ISearchSyncService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ESIndexRepository ESIndexRepository;

    @Override
    public void executeUpsert(ESIndexEntity entity, EnumSet<UpsertField> fields) {
        if (entity == null || StrUtil.isBlank(entity.getId())) {
            log.warn("searchIndex upsert skipped reason=missing-id");
            return;
        }
        Document document = Document.create();

        // resourceId / resourceType / updateTime 始终落盘，否则 Upsert 出来的新文档会缺关键字段
        if (StrUtil.isNotBlank(entity.getResourceId())) {
            document.put(SearchConstants.FIELD_RESOURCE_ID, entity.getResourceId());
        }
        if (StrUtil.isNotBlank(entity.getResourceType())) {
            document.put(SearchConstants.FIELD_RESOURCE_TYPE, entity.getResourceType());
        }
        document.put(SearchConstants.FIELD_UPDATE_TIME, LocalDateTime.now());

        if (fields.contains(UpsertField.RESOURCE_NAME) && StrUtil.isNotBlank(entity.getResourceName())) {
            document.put(SearchConstants.FIELD_RESOURCE_NAME, entity.getResourceName());
        }
        if (fields.contains(UpsertField.CONTENT) && StrUtil.isNotBlank(entity.getContent())) {
            document.put(SearchConstants.FIELD_CONTENT, entity.getContent());
        }
        if (fields.contains(UpsertField.ACL)) {
            // allowedUsers / groupAcls 允许空集合（语义=清空白名单），但必须落到 ES 而非保留旧值
            if (entity.getAllowedUsers() != null) {
                document.put(SearchConstants.FIELD_ALLOWED_USERS, entity.getAllowedUsers());
            }
            if (entity.getGroupAcls() != null) {
                document.put(SearchConstants.FIELD_GROUP_ACLS, entity.getGroupAcls());
            }
        }
        if (fields.contains(UpsertField.TAGS) && entity.getTags() != null) {
            document.put(SearchConstants.FIELD_TAGS, entity.getTags());
        }

        UpdateQuery updateQuery = UpdateQuery.builder(entity.getId())
                .withDocument(document)
                .withDocAsUpsert(true)
                .build();
        IndexCoordinates indexCoordinates = elasticsearchOperations.getIndexCoordinatesFor(ESIndexEntity.class);
        elasticsearchOperations.update(updateQuery, indexCoordinates);

        log.debug("searchIndex upsert done esId={} fields={}", entity.getId(), fields);
    }

    @Override
    public void deleteIndex(String esId) {
        if (StrUtil.isBlank(esId)) {
            return;
        }
        ESIndexRepository.deleteById(esId);
        log.info("searchIndex deleted esId={}", esId);
    }
}
