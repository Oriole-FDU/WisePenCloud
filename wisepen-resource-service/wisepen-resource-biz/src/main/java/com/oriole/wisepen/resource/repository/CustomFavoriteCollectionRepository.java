package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.FavoriteCollectionEntity;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public class CustomFavoriteCollectionRepository {

    private final MongoTemplate mongoTemplate;

    public CustomFavoriteCollectionRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void updateItemCount(Collection<String> collectionIds, int delta) {
        if (collectionIds == null || collectionIds.isEmpty() || delta == 0) return;
        Query query = Query.query(Criteria.where("_id").in(collectionIds));
        Update update = new Update().inc("itemCount", delta);
        mongoTemplate.updateMulti(query, update, FavoriteCollectionEntity.class);
    }
}
