package com.oriole.wisepen.resource.service.impl;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty("wisepen.resource.course-search.maintenance")
public class CourseSearchIndexMaintenance implements ApplicationRunner {
    private static final int BATCH_SIZE = 200;

    private final Environment environment;
    private final MongoTemplate mongoTemplate;
    private final SearchSyncServiceImpl searchSyncService;
    private final ResourceServiceImpl resourceService;

    @Override
    public void run(ApplicationArguments args) {
        String mode = environment.getRequiredProperty("wisepen.resource.course-search.maintenance");
        switch (mode) {
            case "mapping" -> {
                searchSyncService.prepareCourseSearchMapping();
                log.info("course search mapping updated. mode={}", mode);
            }
            case "backfill" -> backfill(environment.getProperty("wisepen.resource.course-search.after", ""));
            default -> throw new IllegalArgumentException("Course search maintenance must be mapping or backfill");
        }
    }

    void backfill(String checkpoint) {
        Object afterId = null;
        boolean scanStringIds = true;
        if (checkpoint.startsWith("str:")) {
            afterId = checkpoint.substring(4);
        } else if (checkpoint.startsWith("oid:")) {
            afterId = new ObjectId(checkpoint.substring(4));
            scanStringIds = false;
        } else if (!checkpoint.isEmpty()) {
            throw new IllegalArgumentException("Expected a course search checkpoint starting with str: or oid:");
        }

        searchSyncService.prepareCourseSearchMapping();
        if (scanStringIds) {
            backfillIdRange(afterId == null ? "" : afterId, afterId != null);
            afterId = null;
        }
        backfillIdRange(afterId == null ? new ObjectId(new byte[12]) : afterId, afterId != null);
    }

    private void backfillIdRange(Object afterId, boolean exclusive) {
        var resources = mongoTemplate.getCollection(mongoTemplate.getCollectionName(ResourceItemEntity.class));
        long indexed = 0;
        long checked = 0;
        String checkpoint = null;
        while (true) {
            // MongoDB 按 BSON 类型限制范围比较，字符串与 ObjectId 要分别扫描。
            var cursor = exclusive ? Filters.gt("_id", afterId) : Filters.gte("_id", afterId);
            List<Document> batch = resources.find(Filters.and(Filters.eq("deletedAt", null), cursor))
                    .sort(Sorts.ascending("_id")).limit(BATCH_SIZE).into(new ArrayList<>());
            if (batch.isEmpty()) break;

            for (Document source : batch) {
                try {
                    ResourceItemEntity resource = mongoTemplate.getConverter().read(ResourceItemEntity.class, source);
                    if (resource.getGroupBinds() != null
                            && resource.getGroupBinds().stream().anyMatch(ESIndexEntity::isGroupSearchPlacement)) {
                        // 旧资源可能还没有预计算权限。按当前标签和组设置生成搜索投影。
                        resource.setComputedGroupAcls(resourceService.computeGroupAcls(resource));
                        searchSyncService.backfillCourseMetadata(resource);
                        indexed++;
                    }
                } catch (RuntimeException e) {
                    log.error("course search backfill failed. resourceId={} checkpoint={} indexed={} failed=1",
                            source.get("_id"), checkpoint, indexed, e);
                    throw e;
                }
                checked++;
                afterId = source.get("_id");
                checkpoint = (afterId instanceof ObjectId ? "oid:" : "str:") + afterId;
            }
            exclusive = true;
            log.info("course search backfill batch finished. checkpoint={} checked={} indexed={} failed=0",
                    checkpoint, checked, indexed);
        }
        log.info("course search backfill range finished. checkpoint={} checked={} indexed={} failed=0",
                checkpoint, checked, indexed);
    }
}
