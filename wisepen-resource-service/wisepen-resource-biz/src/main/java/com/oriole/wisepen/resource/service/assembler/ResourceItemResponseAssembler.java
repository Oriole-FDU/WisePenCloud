package com.oriole.wisepen.resource.service.assembler;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.ComputedGroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.MarketSaleInfo;
import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import com.oriole.wisepen.resource.domain.dto.res.MarketSaleTierResponse;
import com.oriole.wisepen.resource.domain.dto.res.MarketSaleInfoResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceTagBindResponse;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.oriole.wisepen.resource.enums.ResourceAction.MARKET_FORBIDDEN_ACTIONS_MASK;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceItemResponseAssembler {

    private final TagRepository tagRepository;
    private final RemoteUserService remoteUserService;

    public ResourceItemResponse assembleOne(
            ResourceItemEntity entity,
            String currentUserId,
            Map<Long, GroupRoleType> groupRoles,
            List<ResourceAction> requiredResourceActions,
            Integer targetVersion,
            boolean checkMarketTargetVersion) {
        if (entity == null) return null;
        List<ResourceItemResponse> responses = assembleMany(List.of(entity), currentUserId, groupRoles, requiredResourceActions, targetVersion, checkMarketTargetVersion);
        return (responses != null && !responses.isEmpty()) ? responses.getFirst() : null;
    }

    public List<ResourceItemResponse> assembleMany(
            List<ResourceItemEntity> entities,
            String currentUserId,
            Map<Long, GroupRoleType> groupRoles,
            List<ResourceAction> requiredResourceActions,
            Integer targetVersion,
            boolean checkMarketTargetVersion) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 先处理权限过滤问题
        Map<String, List<ResourceAction>> actionsMap = new HashMap<>();
        entities = entities.stream().filter(entity->{
            List<ResourceAction> actions = ResourceAction.permissionCodeToActions(resolveAccess(entity, currentUserId, groupRoles, targetVersion, checkMarketTargetVersion).getActionsMask());
            actionsMap.put(entity.getResourceId(), actions);
            return new HashSet<>(actions).containsAll(requiredResourceActions);
        }).toList();

        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, UserDisplayBase> ownerInfoMap = fetchOwnersInfo(entities);
        Map<Long, GroupDisplayBase> overrideGroupInfoMap = fetchOverrideGroupInfo(entities, currentUserId);
        Map<Long, UserDisplayBase> specifiedUserInfoMap = fetchSpecifiedUserInfo(entities, currentUserId);
        // ResourceID : List<GroupTagBind>
        Map<String, List<GroupTagBind>> accessibleGroupBindsMap = getAccessibleGroupBindsMap(entities, currentUserId, groupRoles);
        Set<String> allTagIds = accessibleGroupBindsMap.values().stream().flatMap(List::stream).map(GroupTagBind::getTagIds).flatMap(List::stream).collect(Collectors.toSet());
        // TagID : TagInfo
        Map<String, TagInfoBase> tagInfosMap = getResourcesTagInfosMap(allTagIds);

        return entities.stream().map(entity->{
            ResourceItemResponse response = BeanUtil.copyProperties(entity, ResourceItemResponse.class);
            if (response.getMarketSaleInfos() == null) response.setMarketSaleInfos(new HashMap<>());

            // 获取已解析的 CurrentActions
            response.setCurrentActions(actionsMap.get(entity.getResourceId()));
            // 解析 OwnerInfo
            response.setOwnerInfo(resolveOwnerInfo(entity, ownerInfoMap));
            // 解析按组分隔的 Tag 绑定
            response.setTagBinds(resolveTagBinds(accessibleGroupBindsMap.getOrDefault(entity.getResourceId(), Collections.emptyList()), tagInfosMap));
            // 解析 MarketSaleInfos
            List<Map<String, MarketSaleInfoResponse>> marketSaleInfos = resolveMarketSaleInfo(entity.getGroupBinds());

            // 仅所有者有此字段
            if (Objects.equals(currentUserId, entity.getOwnerId())) {
                // 处理权限掩码解包
                if (entity.getOverrideGrantedActionsMask() != null) {
                    List<ResourceItemResponse.GroupGrantedActionsResponse> overrideGrantedActions =
                            entity.getOverrideGrantedActionsMask().entrySet().stream().map(entry ->
                                    ResourceItemResponse.GroupGrantedActionsResponse.builder()
                                        .groupId(entry.getKey())
                                        .groupInfo(overrideGroupInfoMap.get(Long.valueOf(entry.getKey())))
                                        .grantedActions(ResourceAction.permissionCodeToActions(entry.getValue())).build()
                            ).toList();
                    response.setOverrideGrantedActions(overrideGrantedActions);
                }
                if (entity.getSpecifiedUsersGrantedActionsMask() != null) {
                    List<ResourceItemResponse.SpecifiedUserGrantedActionsResponse> specifiedUsersGrantedActions =
                            entity.getSpecifiedUsersGrantedActionsMask().entrySet().stream().map(entry ->
                                    ResourceItemResponse.SpecifiedUserGrantedActionsResponse.builder()
                                            .userId(entry.getKey())
                                            .userInfo(specifiedUserInfoMap.get(Long.valueOf(entry.getKey())))
                                            .grantedActions(ResourceAction.permissionCodeToActions(entry.getValue())).build()
                            ).toList();
                    response.setSpecifiedUsersGrantedActions(specifiedUsersGrantedActions);
                }
                // 提供全部 MarketSaleInfo 信息
                marketSaleInfos.getFirst().forEach((groupId, marketSaleInfo) -> response.getMarketSaleInfos().put(groupId, marketSaleInfo));
                marketSaleInfos.getLast().forEach((groupId, marketSaleInfo) -> response.getMarketSaleInfos().put(groupId, marketSaleInfo));
            } else {
                // 提供 MarketSaleInfo 信息
                // 仅提供用户所在集市组的已上架的 MarketSaleInfo 信息
                marketSaleInfos.getFirst().forEach((groupId, marketSaleInfo) -> {
                    if (groupRoles.get(Long.valueOf(groupId)) != null) {
                        response.getMarketSaleInfos().put(groupId, marketSaleInfo);
                    }
                });
                // 提供用户所在集市组的未上架的 MarketSaleInfo 信息（当用户是集市组的管理员时）
                marketSaleInfos.getLast().forEach((groupId, marketSaleInfo) -> {
                    GroupRoleType groupRole = groupRoles.get(Long.valueOf(groupId));
                    if (groupRole == GroupRoleType.ADMIN || groupRole == GroupRoleType.OWNER) {
                        response.getMarketSaleInfos().put(groupId, marketSaleInfo);
                    }
                });
            }

            if (!response.getCurrentActions().contains(ResourceAction.VIEW)) {
                // 预览仅对有 VIEW 权限的用户生效
                response.setPreview(null);
            }

            return response;
        }).toList();
    }

    // 远程批量请求所有者信息
    private Map<Long, UserDisplayBase> fetchOwnersInfo(List<ResourceItemEntity> entities) {
        List<Long> ownerIds = entities.stream()
                .map(ResourceItemEntity::getOwnerId)
                .filter(StringUtils::hasText)
                .map(Long::valueOf).distinct().toList();
        try {
            Map<Long, UserDisplayBase> fetched = remoteUserService.getUserDisplayInfo(ownerIds).getData();
            return fetched == null ? Collections.emptyMap() : fetched;
        } catch (Exception e) {
            log.warn("owner info batch degraded. ownerCount={}", ownerIds.size(), e);
            return Collections.emptyMap();
        }
    }

    // 远程批量请求小组信息
    private Map<Long, GroupDisplayBase> fetchOverrideGroupInfo(List<ResourceItemEntity> entities, String currentUserId) {
        List<Long> groupIds = entities.stream()
                .filter(entity -> Objects.equals(currentUserId, entity.getOwnerId()))
                .map(ResourceItemEntity::getOverrideGrantedActionsMask).filter(Objects::nonNull).flatMap(mask -> mask.keySet().stream())
                .map(Long::valueOf).distinct().toList();
        if (groupIds.isEmpty()) return Collections.emptyMap();
        try {
            Map<Long, GroupDisplayBase> fetched = remoteUserService.getGroupDisplayInfo(groupIds).getData();
            return fetched == null ? Collections.emptyMap() : fetched;
        } catch (Exception e) {
            log.warn("override group info batch degraded. groupCount={}", groupIds.size(), e);
            return Collections.emptyMap();
        }
    }

    // 远程批量请求特殊权限用户信息
    private Map<Long, UserDisplayBase> fetchSpecifiedUserInfo(List<ResourceItemEntity> entities, String currentUserId) {
        List<Long> userIds = entities.stream()
                .filter(entity -> Objects.equals(currentUserId, entity.getOwnerId()))
                .map(ResourceItemEntity::getSpecifiedUsersGrantedActionsMask).filter(Objects::nonNull).flatMap(mask -> mask.keySet().stream())
                .map(Long::valueOf).distinct().toList();
        if (userIds.isEmpty()) return Collections.emptyMap();
        try {
            Map<Long, UserDisplayBase> fetched = remoteUserService.getUserDisplayInfo(userIds).getData();
            return fetched == null ? Collections.emptyMap() : fetched;
        } catch (Exception e) {
            log.warn("specified user info batch degraded. userCount={}", userIds.size(), e);
            return Collections.emptyMap();
        }
    }

    // 获取用户有权访问的资源标签绑定
    private Map<String, List<GroupTagBind>> getAccessibleGroupBindsMap(List<ResourceItemEntity> entities,
                                                                       String currentUserId,
                                                                       Map<Long, GroupRoleType> groupRoles) {
        Map<String, List<GroupTagBind>> accessibleGroupBindsMap = new HashMap<>();
        // 收集用户有权访问的组 (包括个人组)
        Set<String> accessibleGroupIds = groupRoles == null ? new HashSet<>() : groupRoles.keySet().stream().map(String::valueOf).collect(Collectors.toSet());
        accessibleGroupIds.add(ResourceConstants.PERSONAL_GROUP_PREFIX + currentUserId);

        for (ResourceItemEntity entity : entities) {
            // 收集资源绑定的、在用户有权访问的组中的标签绑定
            List<GroupTagBind> accessibleGroupBinds = entity.getGroupBinds() == null ? Collections.emptyList() :
                    entity.getGroupBinds().stream()
                    .filter(bind -> accessibleGroupIds.contains(bind.getGroupId()))
                    .filter(bind -> bind.getTagIds() != null && !bind.getTagIds().isEmpty())
                    .toList();
            accessibleGroupBindsMap.put(entity.getResourceId(), accessibleGroupBinds);
        }
        return accessibleGroupBindsMap;
    }

    // 批量获取标签名
    private Map<String, TagInfoBase> getResourcesTagInfosMap(Set<String> allTagIds) {
        if (allTagIds.isEmpty()) return Collections.emptyMap();
        Map<String, TagInfoBase> tagNamesMap = new HashMap<>();
        Iterable<TagEntity> tagEntities = tagRepository.findAllById(allTagIds);
        for (TagEntity tagEntity : tagEntities) {
            tagNamesMap.put(tagEntity.getTagId(), BeanUtil.copyProperties(tagEntity, TagInfoBase.class));
        }
        return tagNamesMap;
    }


    @Getter
    @AllArgsConstructor
    public static class ResolvedResourceAccess {
        private final ResourceAccessRole resourceAccessRole;
        private final Set<String> permissionSources;
        private final int actionsMask;
    }

    // 预计算 ACL 快速鉴权 (拦截非法越权访问)
    public ResolvedResourceAccess resolveAccess(
            ResourceItemEntity entity,
            String currentUserId,
            Map<Long, GroupRoleType> groupRoles,
            Integer targetVersion,
            boolean checkMarketTargetVersion) {
        // 资源所有者有全部权限
        if (currentUserId.equals(entity.getOwnerId())) {
            return new ResolvedResourceAccess(ResourceAccessRole.OWNER, Collections.emptySet(), ResourceAction.ALL_ACTIONS);
        }

        // 检查资源级的“指定用户特权”
        Integer specifiedMask = entity.getSpecifiedUsersGrantedActionsMask() == null ? null : entity.getSpecifiedUsersGrantedActionsMask().get(currentUserId);
        if (specifiedMask != null) {
            return new ResolvedResourceAccess(ResourceAccessRole.OWNER_SPECIFIED, Collections.emptySet(), specifiedMask);
        }

        // 判断是否缺乏群组上下文（用户不在任何组 或 资源不在任何组）
        if ((groupRoles == null || groupRoles.isEmpty()) && (entity.getGroupBinds() == null || entity.getGroupBinds().isEmpty())){
            // 如果没有群组上下文，也没有被单独赋予“指定用户特权”，直接拒绝
            return new ResolvedResourceAccess(ResourceAccessRole.NONE, Collections.emptySet(), 0);
        }

        Set<String> permissionSources = new HashSet<>();
        int groupActionsMask = 0;
        ResourceAccessRole groupResourceAccessRole = ResourceAccessRole.NONE;
        // 计算群组权限
        if (entity.getGroupBinds() != null && entity.getComputedGroupAcls() != null && groupRoles != null) {
            for (Map.Entry<String, ComputedGroupAcl> entry : entity.getComputedGroupAcls().entrySet()) { // 遍历预计算的群组 ACL
                String groupId = entry.getKey();
                GroupRoleType groupRole = groupRoles.get(Long.valueOf(groupId));

                if (groupRole == null) continue; // 用户不在该组，跳过

                MarketSaleInfo marketSaleInfo = entity.getGroupBinds().stream()
                        .filter(bind -> Objects.equals(bind.getGroupId(), groupId))
                        .map(GroupTagBind::getMarketSaleInfo)
                        .filter(Objects::nonNull).findFirst().orElse(null);

                // 用户是组管理员/拥有者，有全部权限
                if (groupRole == GroupRoleType.ADMIN || groupRole == GroupRoleType.OWNER) {
                    if (marketSaleInfo != null) {
                        // 不能存在 MARKET_FORBIDDEN_ACTIONS_MASK 中的权限
                        permissionSources.add(groupId);
                        groupActionsMask = ResourceAction.ALL_ACTIONS & ~MARKET_FORBIDDEN_ACTIONS_MASK;
                        groupResourceAccessRole = ResourceAccessRole.GROUP_ADMIN;
                    } else {
                        permissionSources.add(groupId);
                        groupActionsMask = ResourceAction.ALL_ACTIONS;
                        groupResourceAccessRole = ResourceAccessRole.GROUP_ADMIN;
                    }
                    break;
                }
                // 在当前组是市场组时，检查目标版本是否适用
                if (marketSaleInfo != null && checkMarketTargetVersion) {
                    if (targetVersion == null || !Objects.equals(targetVersion, marketSaleInfo.getOfferVersion())) {
                        continue;
                    }
                }

                // 提取预计算ACL
                ComputedGroupAcl acl = entry.getValue();
                Integer resolvedGroupMask = acl.getUserMasks().getOrDefault(currentUserId, acl.getBaseMask());

                // 只要有一个组能下发权限（无权限(0)不在此列），基础身份就是 GROUP_MEMBER
                if (resolvedGroupMask != 0) {
                    permissionSources.add(groupId);
                    // 累加普通成员在不同小组下获得的权限 (按位或)
                    groupActionsMask |= resolvedGroupMask;
                    groupResourceAccessRole = ResourceAccessRole.GROUP_MEMBER;
                }
            }
        }

        return new ResolvedResourceAccess(groupResourceAccessRole, permissionSources, groupActionsMask);
    }

    private UserDisplayBase resolveOwnerInfo(ResourceItemEntity entity, Map<Long, UserDisplayBase> ownerInfoMap) {
        Long owner = Long.valueOf(entity.getOwnerId());
        UserDisplayBase ownerInfo = ownerInfoMap.get(owner);
        return ownerInfo == null ? new UserDisplayBase("UNKNOWN") : ownerInfo;
    }

    private Map<String, TagInfoBase> resolveTags(List<String> tagIds, Map<String, TagInfoBase> tagMap) {
        Map<String, TagInfoBase> tags = new LinkedHashMap<>();
        for (String tagId : tagIds) {
            tags.put(tagId, tagMap.getOrDefault(tagId, TagInfoBase.builder().tagName("UNKNOWN").build()));
        }
        return tags;
    }

    private List<ResourceTagBindResponse> resolveTagBinds(List<GroupTagBind> groupBinds, Map<String, TagInfoBase> tagMap) {
        return groupBinds.stream().map(groupBind -> {
            ResourceTagBindResponse tagBindResponse = new ResourceTagBindResponse();
            tagBindResponse.setGroupId(groupBind.getGroupId());
            tagBindResponse.setPrimaryTagId(groupBind.getTagIds().getFirst());
            tagBindResponse.setAiBillingApproved(Boolean.TRUE.equals(groupBind.getAiBillingApproved()));
            tagBindResponse.setTags(resolveTags(groupBind.getTagIds(), tagMap));
            return tagBindResponse;
        }).toList();
    }

    private List<Map<String, MarketSaleInfoResponse>> resolveMarketSaleInfo(List<GroupTagBind> groupBinds) {
        Map<String, MarketSaleInfoResponse> onShelf = new HashMap<>();
        Map<String, MarketSaleInfoResponse> notOnShelf = new HashMap<>();

        if (groupBinds == null) return List.of(onShelf, notOnShelf);

        groupBinds.forEach(bind -> {
            if (bind.getMarketSaleInfo() != null) {
                MarketSaleInfo marketSaleInfo = bind.getMarketSaleInfo();
                // MarketSaleInfo 转换为 MarketSaleInfoResponse
                MarketSaleInfoResponse marketSaleInfoResponse = BeanUtil.copyProperties(marketSaleInfo, MarketSaleInfoResponse.class);
                if (marketSaleInfo.getReviewActionsMask() != null) { // 若未设置应保持为空
                    marketSaleInfoResponse.setReviewActions(ResourceAction.permissionCodeToActions(marketSaleInfo.getReviewActionsMask()));
                }
                List<MarketSaleTierResponse> marketSaleTierList = marketSaleInfo.getMarketSaleTiers() == null ? Collections.emptyList() : marketSaleInfo.getMarketSaleTiers().stream().map(marketSaleTier -> {
                    MarketSaleTierResponse marketSaleTierResponse = BeanUtil.copyProperties(marketSaleTier, MarketSaleTierResponse.class);
                    marketSaleTierResponse.setGrantedActions(ResourceAction.permissionCodeToActions(marketSaleTier.getGrantedActionsMask()));
                    return marketSaleTierResponse;
                }).toList();
                marketSaleInfoResponse.setMarketSaleTiers(marketSaleTierList);
                // 拆分是否已经上架
                if (marketSaleInfo.getStatus() == MarketSaleStatus.PUBLISHED) {
                    onShelf.put(bind.getGroupId(), marketSaleInfoResponse);
                } else {
                    notOnShelf.put(bind.getGroupId(), marketSaleInfoResponse);
                }
            }
        });
        return List.of(onShelf, notOnShelf);
    }
}

