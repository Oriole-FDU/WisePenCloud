package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemBase;
import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemReactionBase;
import com.oriole.wisepen.resource.domain.entity.ResourceInlineCommentEntity;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CustomResourceInlineCommentRepository {

    private final MongoTemplate mongoTemplate;

    public CustomResourceInlineCommentRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ResourceInlineCommentEntity> listInlineComments(String resourceId,
                                                                Integer contentVersion,
                                                                Boolean resolved) {
        Criteria criteria = Criteria.where("resourceId").is(resourceId);
        if (contentVersion != null) {
            criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("applicableFromVersion").is(null),
                            Criteria.where("applicableFromVersion").lte(contentVersion)
                    ),
                    new Criteria().orOperator(
                            Criteria.where("applicableToVersion").is(null),
                            Criteria.where("applicableToVersion").gte(contentVersion)
                    )
            );
        }
        if (resolved != null) {
            criteria.and("resolved").is(resolved);
        }

        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "updateTime"));
        return mongoTemplate.find(query, ResourceInlineCommentEntity.class);
    }

    public void appendItem(String resourceId, String inlineCommentId, ResourceInlineCommentItemBase item) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId));
        Update update = new Update()
                .push("items", item)
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void deleteInlineComment(String resourceId, String inlineCommentId) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId));
        mongoTemplate.remove(query, ResourceInlineCommentEntity.class);
    }

    public void deleteItem(String resourceId, String inlineCommentId, String itemId) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId));
        Update update = new Update()
                .pull("items", new Document("itemId", itemId))
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void updateItem(String resourceId, String inlineCommentId, String itemId, String authorId,
                           String content, List<String> imageUrls, List<String> mentionUserIds) {
        Criteria itemCriteria = Criteria.where("itemId").is(itemId).and("authorId").is(authorId);
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId)
                .and("items").elemMatch(itemCriteria));
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update()
                .set("items.$.content", content)
                .set("items.$.imageUrls", imageUrls)
                .set("items.$.mentionUserIds", mentionUserIds)
                .set("items.$.updateTime", now)
                .set("updateTime", now);
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void setItemReactions(String resourceId, String inlineCommentId, String itemId,
                                 String operatorUserId, List<ResourceInlineCommentItemReactionBase> reactions) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId)
                .and("items").elemMatch(Criteria.where("itemId").is(itemId)));
        Update update = reactions.isEmpty()
                ? new Update().unset("items.$.reactions." + operatorUserId)
                : new Update().set("items.$.reactions." + operatorUserId, reactions);
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void resolveInlineComment(String resourceId, String inlineCommentId, String resolvedBy) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId));
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update()
                .set("resolved", true)
                .set("resolvedBy", resolvedBy)
                .set("resolvedAt", now)
                .set("updateTime", now);
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void unresolveInlineComment(String resourceId, String inlineCommentId) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId));
        Update update = new Update()
                .set("resolved", false)
                .unset("resolvedBy")
                .unset("resolvedAt")
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }
}
