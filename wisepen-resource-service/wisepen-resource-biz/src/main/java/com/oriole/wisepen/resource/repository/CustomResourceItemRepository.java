package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.VisibilityModeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class CustomResourceItemRepository {

    private final MongoTemplate mongoTemplate;

    public CustomResourceItemRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // 核心分页查询
    public Page<ResourceItemEntity> findAccessibleResources(
            String userId, String groupId, GroupRoleType userGroupRole, List<String> tagIds, QueryLogicEnum tagQueryLogicMode,
            String resourceType, Pageable pageable) {

        Criteria criteria = new Criteria();

        // 资源类型过滤
        if (StringUtils.hasText(resourceType)) {
            criteria.and("resourceType").is(resourceType);
        }

        if (!StringUtils.hasText(groupId)) { // 个人检索
            // 个人模式不传 groupId，只要是自己创建的文件都属于个人空间
            criteria.and("ownerId").is(userId);
            if (tagIds != null && !tagIds.isEmpty()) {
                if (tagQueryLogicMode == QueryLogicEnum.AND) {
                    criteria.and("groupBinds.tagIds").all(tagIds);
                } else {
                    criteria.and("groupBinds.tagIds").in(tagIds);
                }
            }

        } else { // 小组检索
            Criteria groupBindCriteria = Criteria.where("groupId").is(groupId);
            if (tagIds != null && !tagIds.isEmpty()) {
                if (tagQueryLogicMode == QueryLogicEnum.AND) {
                    groupBindCriteria.and("tagIds").all(tagIds);
                } else {
                    groupBindCriteria.and("tagIds").in(tagIds);
                }
            }
            criteria.and("groupBinds").elemMatch(groupBindCriteria);
            if (userGroupRole != GroupRoleType.ADMIN && userGroupRole != GroupRoleType.OWNER) {
                Criteria aclCriteria = new Criteria().orOperator(
                        // 情况 A: 自己的文件
                        Criteria.where("ownerId").is(userId),
                        // 情况 B: 在用户所在的组内，模式为 ALL
                        Criteria.where("computedAcls").elemMatch(
                                Criteria.where("groupId").in(groupId).and("visibilityMode").is(VisibilityModeEnum.ALL)
                        ),
                        // 情况 C: 模式为 WHITELIST，且用户在名单中
                        Criteria.where("computedAcls").elemMatch(
                                Criteria.where("groupId").in(groupId)
                                        .and("visibilityMode").in(VisibilityModeEnum.WHITELIST)
                                        .and("specifiedUsers").is(userId)
                        ),
                        // 情况 D: 模式为 BLACKLIST，且用户【不在】名单中
                        Criteria.where("computedAcls").elemMatch(
                                Criteria.where("groupId").in(groupId)
                                        .and("visibilityMode").is(VisibilityModeEnum.BLACKLIST)
                                        .and("specifiedUsers").ne(userId)
                        )
                );
                criteria = new Criteria().andOperator(criteria, aclCriteria);
            }
        }

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, ResourceItemEntity.class);

        query.with(pageable);
        List<ResourceItemEntity> list = mongoTemplate.find(query, ResourceItemEntity.class);

        return new PageImpl<>(list, pageable, total);
    }
}