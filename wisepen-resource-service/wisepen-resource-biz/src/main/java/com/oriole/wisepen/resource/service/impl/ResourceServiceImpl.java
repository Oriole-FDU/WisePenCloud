package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.GroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.ResourceSortByEnum;
import com.oriole.wisepen.resource.enums.VisibilityModeEnum;
import com.oriole.wisepen.resource.exception.ResPermissionErrorCode;
import com.oriole.wisepen.resource.repository.CustomResourceItemRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.resource.service.IAclEventPublisher;
import com.oriole.wisepen.resource.service.IResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements IResourceService {

    private final TagRepository tagRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final CustomResourceItemRepository customResourceItemRepository;
    private final IAclEventPublisher aclEventPublisher;
    private final MongoTemplate mongoTemplate;

    @Override
    public void assertResourceOwner(String resourceId, String userId) {
        ResourceItemEntity entity = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));
        if (!userId.equals(entity.getOwnerId())) {
            throw new ServiceException(ResPermissionErrorCode.RESOURCE_PERMISSION_DENIED);
        }
    }

    @Override
    public void renameResource(ResourceRenameRequest req) {
        ResourceItemEntity entity = resourceItemRepository.findById(req.getResourceId())
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));

        entity.setResourceName(req.getNewName());
        entity.setUpdateTime(new Date());
        resourceItemRepository.save(entity);
    }

    @Override
    public void updateResourceTags(ResourceUpdateTagsRequest req) {
        String resourceId = req.getResourceId();
        String groupId = req.getGroupId();
        List<String> tagIds = req.getTagIds();

        ResourceItemEntity entity = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));

        entity.setUpdateTime(new Date());
        List<GroupTagBind> groupBinds = entity.getGroupBinds();

        if (tagIds == null || tagIds.isEmpty()) {
            // 本次操作清空了该组所有标签，从列表中移除该组
            groupBinds.removeIf(bind -> bind.getGroupId().equals(groupId));
        } else {
            // 执行常规的覆盖 (Update)/新增 (Upsert) 操作

            // 检查Tag是否存在
            List<TagEntity> validTags = (List<TagEntity>) tagRepository.findAllById(req.getTagIds());
            if (validTags.size() != req.getTagIds().size()) {
                throw new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND); // 包含无效的标签ID
            }
            boolean allBelongToGroup = validTags.stream().allMatch(tag -> groupId.equals(tag.getGroupId()));
            if (!allBelongToGroup) {
                throw new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND); // 包含无效的标签ID（实际上是跨空间越权挂载，但不返回真实原因）
            }

            // 寻找该实体中是否已经存在当前 groupId 的绑定记录
            boolean groupFound = false;
            for (GroupTagBind groupBind : groupBinds) {
                if (groupBind.getGroupId().equals(groupId)) {
                    // 找到对应的组，直接覆盖该组的 tagIds
                    groupBind.setTagIds(tagIds);
                    groupFound = true;
                    break;
                }
            }
            // 如果之前这个资源没有在这个组下绑过标签，则新增一个组绑定对象
            if (!groupFound) {
                entity.getGroupBinds().add(GroupTagBind.builder().groupId(groupId).tagIds(tagIds).build());
            }
        }
        resourceItemRepository.save(entity);
        if (groupId != null && groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
            return; // 个人Tag变更不需要重新计算Acl
        }
        this.calculateResourceAcl(entity.getResourceId());
    }

    @Override
    public PageResult<ResourceItemResponse> listResources(String currentUserId,
                                                          String groupId, GroupRoleType userGroupRole,
                                                          List<String> tagIds, QueryLogicEnum tagQueryLogicMode,
                                                          String resourceType, int page, int size,
                                                          ResourceSortByEnum sortBy, SortDirectionEnum sortDir) {

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(sortDir.toSpringDirection(), sortBy.getDbField()));

        Page<ResourceItemEntity> entityPage = customResourceItemRepository.findAccessibleResources(
                currentUserId, groupId, userGroupRole, tagIds, tagQueryLogicMode, resourceType, pageable);

        List<ResourceItemResponse> responses = entityPage.getContent().stream().map(entity -> {
            ResourceItemResponse resp = new ResourceItemResponse();
            BeanUtil.copyProperties(entity, resp);

            // 提取当前上下文的 Tag 回显给前端
            resp.setCurrentTags(extractRelevantTagIds(entity, groupId));

            return resp;
        }).collect(Collectors.toList());

        PageResult<ResourceItemResponse> pageResult = new PageResult<>(page, size, (int) entityPage.getTotalElements());
        pageResult.addAll(responses);
        return pageResult;
    }

    // 辅助方法：提取资源的 TagId 供前端回显
    private List<String> extractRelevantTagIds(ResourceItemEntity entity, String groupId) {
        if (entity.getGroupBinds() == null) {
            return Collections.emptyList();
        }

        if (StringUtils.hasText(groupId)) {
            // 如果处于某个特定的小组内，只关心该文件在这个小组下挂了什么标签
            return entity.getGroupBinds().stream()
                    .filter(bind -> bind.getGroupId().equals(groupId))
                    .findFirst()
                    .map(GroupTagBind::getTagIds)
                    .orElse(Collections.emptyList());
        } else {
            // 否则提取该文件在所有空间下挂载的全部标签，并去重
            return entity.getGroupBinds().stream()
                    .map(GroupTagBind::getTagIds)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    @Override
    public String createResourceItem(ResourceCreateDTO dto) {
        ResourceItemEntity entity = new ResourceItemEntity();
        BeanUtil.copyProperties(dto, entity);

        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());

        resourceItemRepository.save(entity);
        return entity.getResourceId();
    }

    @Override
    public void removeResourceItem(String resourceId) {
        resourceItemRepository.findById(resourceId).ifPresent(entity -> {
            // 插入到回收站集合中
            mongoTemplate.save(entity, "wisepen_resource_trash");
            resourceItemRepository.deleteById(resourceId);
        });
    }

    @Override
    public void updateResourceAttributes(ResourceUpdateDTO dto) {
        resourceItemRepository.findById(dto.getResourceId()).ifPresent(entity -> {
            BeanUtil.copyProperties(dto, entity, CopyOptions.create().ignoreNullValue());
            entity.setUpdateTime(new Date());
            resourceItemRepository.save(entity);
        });
    }

    @Override
    public void afterTagNodeChanged(List<String> changedTagIds) {
        if (changedTagIds == null || changedTagIds.isEmpty()) return;

        // 查询所有涉及的资源绑定记录
        List<ResourceItemEntity> affectedBinds = resourceItemRepository.findByTagIdsIn(changedTagIds);

        // 循环触发权限预计算
        // 个人Tag不能设置权限，无法触发afterTagNodeChanged，因此无需特意排除
        for (ResourceItemEntity bind : affectedBinds) {
            aclEventPublisher.publishRecalculateEvent(bind.getResourceId(), "TAG_CHANGED");
        }
    }

    @Override
    public void afterTagNodeDeleted(List<String> deletedTagIds, Boolean isPersonalTag) {
        if (deletedTagIds == null || deletedTagIds.isEmpty()) return;

        // 查询所有涉及的资源绑定记录
        List<ResourceItemEntity> affectedBinds = resourceItemRepository.findByTagIdsIn(deletedTagIds);

        for (ResourceItemEntity entity : affectedBinds) {
            if (entity.getGroupBinds() != null) {
                Iterator<GroupTagBind> iterator = entity.getGroupBinds().iterator();
                while (iterator.hasNext()) {
                    GroupTagBind groupBind = iterator.next();

                    // 移除掉已经被删除的 Tag ID
                    if (groupBind.getTagIds() != null) {
                        groupBind.getTagIds().removeAll(deletedTagIds);
                    }

                    // 如果移除后，该组下没有任何 Tag 了，清理空组
                    if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) {
                        iterator.remove();
                    }
                }
            }
            resourceItemRepository.save(entity);
            if (isPersonalTag) {
                continue; // 个人Tag变更不需要重新计算Acl
            }
            aclEventPublisher.publishRecalculateEvent(entity.getResourceId(), "TAG_DELETED");
        }
    }

    @Override
    public void calculateResourceAcl(String resourceId) {
        // 获取资源绑定记录
        ResourceItemEntity bindEntity = resourceItemRepository.findByResourceId(resourceId)
                .orElse(null);

        List<GroupAcl> finalGroupAcls = new ArrayList<>();

        if (bindEntity != null && bindEntity.getGroupBinds() != null && !bindEntity.getGroupBinds().isEmpty()) {
            for (GroupTagBind groupBind : bindEntity.getGroupBinds()) {
                if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) {
                    continue;
                }

                if (groupBind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
                    continue; // 个人Tag不参与计算Acl
                }

                // 提取首标并查询
                String primaryTagId = groupBind.getTagIds().get(0);
                TagEntity primaryTag = tagRepository.findById(primaryTagId).orElse(null);

                if (primaryTag == null) continue;

                // 溯源
                TagEntity effectiveTag = findEffectiveTagWithPermission(primaryTag);

                // 装载该组的最终规则
                if (effectiveTag != null) {
                    finalGroupAcls.add(GroupAcl.builder()
                            .groupId(groupBind.getGroupId())
                            .visibilityMode(effectiveTag.getVisibilityMode())
                            .specifiedUsers(effectiveTag.getSpecifiedUsers() != null ? effectiveTag.getSpecifiedUsers() : Collections.emptyList())
                            .build());
                } else {
                    finalGroupAcls.add(GroupAcl.builder()
                            .groupId(groupBind.getGroupId())
                            .visibilityMode(VisibilityModeEnum.ALL)
                            .specifiedUsers(Collections.emptyList())
                            .build());
                }
            }
        }

        Query query = new Query(Criteria.where("_id").is(resourceId));
        Update update = new Update()
                .set("computedAcls", finalGroupAcls)
                .set("updateTime", new Date());
        mongoTemplate.updateFirst(query, update, ResourceItemEntity.class);
    }

    @Override
    public Boolean checkPermission(ResourceCheckPermissionDTO dto) {
        // 如果资源不存在（或已进入回收站），直接拒绝
        ResourceItemEntity entity = resourceItemRepository.findById(dto.getResourceId()).orElse(null);
        if (entity == null) {
            return false;
        }
        // 如果是资源所有者，则直接有权限
        if (dto.getUserId().equals(entity.getOwnerId())) {
            return true;
        }
        // 用户不在任何组，直接拒绝
        if (dto.getGroupRoles() == null || dto.getGroupRoles().isEmpty()) {
            return false;
        }
        // 资源不在任何组，直接拒绝
        if (entity.getGroupBinds() == null || entity.getGroupBinds().isEmpty()) {
            return false;
        }
        for (GroupTagBind groupBind : entity.getGroupBinds()) {
            String groupId = groupBind.getGroupId();
            if (!dto.getGroupRoles().containsKey(groupId)) { // 用户不在该组，跳过
                continue;
            }
            GroupRoleType userRoleInThisGroup = dto.getGroupRoles().get(groupId); // 组管理或所有者直接有权限
            if (userRoleInThisGroup == GroupRoleType.ADMIN || userRoleInThisGroup == GroupRoleType.OWNER) {
                return true;
            }
            VisibilityModeEnum mode = VisibilityModeEnum.ALL; // 默认退化为全员可见
            List<String> specifiedUsers = Collections.emptyList();

            // 同组认首标 (取列表里的第一个 Tag)
            String primaryTagId = groupBind.getTagIds().get(0);
            TagEntity primaryTag = tagRepository.findById(primaryTagId).orElse(null); // 查 Tag 节点
            // 向上溯源找有效权限配置
            TagEntity effectiveTag = findEffectiveTagWithPermission(primaryTag);
            if (effectiveTag != null) {
                mode = effectiveTag.getVisibilityMode();
                specifiedUsers = effectiveTag.getSpecifiedUsers() != null ? effectiveTag.getSpecifiedUsers() : Collections.emptyList();
            }

            if (mode == VisibilityModeEnum.ALL) {
                return true;
            } else if (mode == VisibilityModeEnum.WHITELIST && specifiedUsers.contains(dto.getUserId())) {
                return true;
            } else if (mode == VisibilityModeEnum.BLACKLIST && !specifiedUsers.contains(dto.getUserId())) {
                return true;
            }
        }

        // 遍历了用户所在的所有组，都没有拿到权限，最终拦截
        return false;
    }

    private TagEntity findEffectiveTagWithPermission(TagEntity node) {
        if (node.getVisibilityMode() != null) {
            return node; // 自己就有权限配置，直接返回
        }

        // 自己没有，通过 ancestors 向上找 (从后往前遍历 ancestors 数组，因为最后一个元素是直接父节点)
        List<String> ancestors = node.getAncestors();
        if (ancestors == null || ancestors.isEmpty()) {
            return null; // 到达根节点且无配置
        }

        // 一次性将所有祖先节点查出
        Iterable<TagEntity> ancestorEntities = tagRepository.findAllById(ancestors);

        Map<String, TagEntity> ancestorMap = new HashMap<>(); // 用 Map 建立 ID 索引
        for (TagEntity entity : ancestorEntities) {
            ancestorMap.put(entity.getTagId(), entity);
        }

        for (int i = ancestors.size() - 1; i >= 0; i--) {
            String ancestorId = ancestors.get(i);
            TagEntity ancestorNode = ancestorMap.get(ancestorId);
            // 避免TagEntity ancestorNode = tagRepository.findById(ancestorId).orElse(null);产生多次网络请求
            if (ancestorNode != null && ancestorNode.getVisibilityMode() != null) {
                return ancestorNode; // 找到了最近的一个带有配置的长辈节点
            }
        }
        return null;
    }
}