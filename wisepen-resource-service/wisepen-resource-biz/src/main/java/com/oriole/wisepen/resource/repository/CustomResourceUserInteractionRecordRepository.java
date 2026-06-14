package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.ResourceUserInteractionRecordEntity;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class CustomResourceUserInteractionRecordRepository {

    private final MongoTemplate mongoTemplate;

    public CustomResourceUserInteractionRecordRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private ResourceUserInteractionRecordEntity findAndSetField(String resourceId, String userId, String field, Object value) {
        Query query = Query.query(Criteria.where("resourceId").is(resourceId).and("userId").is(userId));
        Update update = new Update().set(field, value);

        return mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(false),
                ResourceUserInteractionRecordEntity.class);
    }

    /** 原子写入阅读状态 */
    public ResourceUserInteractionRecordEntity findAndSetRead(String resourceId, String userId, boolean read) {
        return findAndSetField(resourceId, userId, "read", read);
    }

    /** 原子写入点赞状态 */
    public ResourceUserInteractionRecordEntity findAndSetLiked(String resourceId, String userId, boolean liked) {
        return findAndSetField(resourceId, userId, "liked", liked);
    }

    /** 原子写入评分 */
    public ResourceUserInteractionRecordEntity findAndSetScore(String resourceId, String userId, int score) {
        return findAndSetField(resourceId, userId, "score", score);
    }
}
