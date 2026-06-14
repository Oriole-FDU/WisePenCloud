package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.ResourceAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
public class CustomResourceItemRepository {

    private final MongoTemplate mongoTemplate;

    public CustomResourceItemRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // 核心分页查询
    public Page<ResourceItemEntity> findAccessibleResources(
            String userId, String groupId, GroupRoleType userGroupRole, List<String> tagIds, List<String> excludeTrashId, QueryLogicEnum tagQueryLogicMode,
            String resourceType, Pageable pageable) {

        List<Criteria> allCriteria = new ArrayList<>();

        if (excludeTrashId != null && !excludeTrashId.isEmpty()) {
            // 排除 groupBinds 数组中，groupId 匹配 且 tagIds 中包含回收站黑名单中任意一个 ID 的记录
            allCriteria.add(Criteria.where("groupBinds").not().elemMatch(
                    Criteria.where("groupId").is(groupId).and("tagIds").in(excludeTrashId)
            ));
        }

        // 资源类型过滤
        if (StringUtils.hasText(resourceType)) {
            allCriteria.add(Criteria.where("resourceType").is(resourceType));
        }

        boolean isPersonalSpace = groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX);

        if (isPersonalSpace) {
            allCriteria.add(Criteria.where("ownerId").is(userId));
        }

        // 统一使用 elemMatch 查询，精确匹配 groupId + tagIds
        Criteria groupBindCriteria = Criteria.where("groupId").is(groupId);
        if (tagIds != null && !tagIds.isEmpty()) {
            if (tagQueryLogicMode == QueryLogicEnum.AND) {
                groupBindCriteria.and("tagIds").all(tagIds);
            } else {
                groupBindCriteria.and("tagIds").in(tagIds);
            }
        }
        allCriteria.add(Criteria.where("groupBinds").elemMatch(groupBindCriteria));

        // ACL 仅对小组生效，个人空间已通过 ownerId 隔离
        if (!isPersonalSpace && userGroupRole != GroupRoleType.ADMIN && userGroupRole != GroupRoleType.OWNER) {
            int discoverCode = ResourceAction.DISCOVER.getCode();
            String aclPrefix = "computedGroupAcls." + groupId;
            Criteria aclCriteria = new Criteria().orOperator(
                    // 情况 A: 自己的文件 (免检)
                    Criteria.where("ownerId").is(userId),
                    // 情况 B: 资源级的定向用户特权 (包含 DISCOVER)
                    Criteria.where("specifiedUsersGrantedActionsMask." + userId).bits().allSet(discoverCode),
                    // 情况 C: 用户被分配了专属掩码，且掩码中包含 DISCOVER
                    Criteria.where(aclPrefix + ".userMasks." + userId).bits().allSet(discoverCode),
                    // 情况 D: 用户没有专属掩码，检查该组的 baseMask 是否包含 DISCOVER
                    new Criteria().andOperator(
                            Criteria.where(aclPrefix + ".userMasks." + userId).exists(false),
                            Criteria.where(aclPrefix + ".baseMask").bits().allSet(discoverCode)
                    )
            );
            allCriteria.add(aclCriteria);
        }

        Criteria criteria = new Criteria().andOperator(allCriteria.toArray(new Criteria[0]));

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, ResourceItemEntity.class);

        query.with(pageable);
        List<ResourceItemEntity> list = mongoTemplate.find(query, ResourceItemEntity.class);

        return new PageImpl<>(list, pageable, total);
    }

    /** 更新阅读量 */
    public void updateReadCount(String resourceId, int delta) {
        updateInteractionField(resourceId, "interactionInfo.readCount", delta);
    }

    /** 更新点赞数 */
    public void updateLikeCount(String resourceId, int delta) {
        updateInteractionField(resourceId, "interactionInfo.likeCount", delta);
    }

    /** 更新收藏数 */
    public void updateFavoriteCount(String resourceId, int delta) {
        updateInteractionField(resourceId, "interactionInfo.favoriteCount", delta);
    }

    public void updateFavoriteCount(List<String> resourceIds, int delta) {
        if (resourceIds.isEmpty()) return;
        Query query = Query.query(Criteria.where("_id").in(resourceIds));
        Update update = new Update().inc("interactionInfo.favoriteCount", delta);
        mongoTemplate.updateMulti(query, update, ResourceItemEntity.class);
    }

    public void updateScore(String resourceId, int scoreCountDelta, int scoreTotalDelta) {
        Query query = Query.query(Criteria.where("_id").is(resourceId));
        Update update = new Update()
                .inc("interactionInfo.scoreCount", scoreCountDelta)
                .inc("interactionInfo.scoreTotal", scoreTotalDelta);
        mongoTemplate.updateFirst(query, update, ResourceItemEntity.class);
    }

    /** 单字段原子 $inc 模板 */
    private void updateInteractionField(String resourceId, String field, int delta) {
        Query query = Query.query(Criteria.where("_id").is(resourceId));
        Update update = new Update().inc(field, delta);
        mongoTemplate.updateFirst(query, update, ResourceItemEntity.class);
    }
}