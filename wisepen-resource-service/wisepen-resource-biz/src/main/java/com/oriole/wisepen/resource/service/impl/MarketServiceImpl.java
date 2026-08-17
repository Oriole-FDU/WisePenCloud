package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.MarketSaleInfo;
import com.oriole.wisepen.resource.domain.base.MarketSaleTierBase;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleAuditRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleOffShelfRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketSalePublishRequest;
import com.oriole.wisepen.resource.domain.dto.req.MarketPurchaseRequest;
import com.oriole.wisepen.resource.domain.dto.res.MarketOrderResponse;
import com.oriole.wisepen.resource.domain.entity.MarketOrderEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.MarketSaleStatus;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.event.MarketResourceIndexDeleteByVersionEvent;
import com.oriole.wisepen.resource.event.MarketResourceIndexUpsertEvent;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.mq.IResourceEventPublisher;
import com.oriole.wisepen.resource.repository.MarketOrderRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.service.IMarketService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.WalletSettleCoinTradeRequest;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import com.oriole.wisepen.user.api.feign.RemoteWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

import static com.oriole.wisepen.resource.constant.ResourceConstants.MARKET_GROUP_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketServiceImpl implements IMarketService {

    private final IResourceService resourceService;

    private final MarketOrderRepository marketOrderRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final IResourceEventPublisher resourceEventPublisher;
    private final RemoteUserService remoteUserService;
    private final RemoteWalletService remoteWalletService;
    private final ApplicationEventPublisher applicationEventPublisher;


    @Override
    // 上架
    public void publishSaleInfo(MarketSalePublishRequest request) {
        ResourceItemEntity resource = resourceService.getResourceEntity(request.getResourceId());

        String marketGroupId = request.getMarketGroupId();
        // 检查 groupID对应的小组是否是 MARKET_GROUP
        Map<Long, GroupDisplayBase> groupMap = remoteUserService.getGroupDisplayInfo(List.of(Long.valueOf(marketGroupId))).getData();
        GroupDisplayBase groupInfo = groupMap == null ? null : groupMap.get(Long.valueOf(marketGroupId));
        if (groupInfo == null || groupInfo.getGroupType() != GroupType.MARKET_GROUP) {
            throw new ServiceException(ResourceError.MARKET_GROUP_NOT_FOUND);
        }

        // MARKET 组的 Tag 在 MARKET_GROUP_PREFIX 前缀的 groupId 下
        resourceService.findAndValidateTags(MARKET_GROUP_PREFIX + marketGroupId, request.getTagIds());

        List<GroupTagBind> groupBinds = resource.getGroupBinds() == null ? new ArrayList<>() : resource.getGroupBinds();
        // 寻找该实体中是否已经存在当前 groupId 的绑定记录
        GroupTagBind groupBind = groupBinds.stream().filter(groupTagBind -> groupTagBind.getGroupId().equals(marketGroupId)).findFirst().orElse(null);
        if (groupBind == null) {
            groupBind = GroupTagBind.builder().groupId(marketGroupId).tagIds(request.getTagIds()).build();
            groupBinds.add(groupBind);
        }
        groupBind.setTagIds(request.getTagIds());

        MarketSaleInfo marketSaleInfo = groupBind.getMarketSaleInfo() == null ? new MarketSaleInfo() : groupBind.getMarketSaleInfo();
        Integer oldOfferVersion = marketSaleInfo.getOfferVersion();

        if (marketSaleInfo.getStatus() == MarketSaleStatus.BANNED) { // 被禁的资源不能上架
            throw new ServiceException(ResourceError.CANNOT_REPUBLISH_BANNED_MARKET_SALE);
        }

        marketSaleInfo.setReviewContentPercentage(request.getReviewContentPercentage());
        // 拒绝带有 EDIT 权限的 ReviewAction
        if (request.getReviewActions() != null && request.getReviewActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(ResourceError.MARKET_ACTIONS_INVALID);
        }
        marketSaleInfo.setReviewActionsMask(request.getReviewActions() == null ? null : ResourceAction.actionsToPermissionCode(request.getReviewActions()));

        Set<Integer> grantedActionMasks = new HashSet<>();
        List<MarketSaleTierBase> marketSaleTiers = new ArrayList<>();
        for (MarketSalePublishRequest.MarketSaleTier marketSaleTier : request.getMarketSaleTiers()) {
            // 拒绝带有 EDIT 权限的 Action
            if (marketSaleTier.getGrantedActions().contains(ResourceAction.EDIT)) {
                throw new ServiceException(ResourceError.MARKET_ACTIONS_INVALID);
            }
            int grantedActionsMask = ResourceAction.actionsToPermissionCode(marketSaleTier.getGrantedActions());
            if (!grantedActionMasks.add(grantedActionsMask)) {
                // 禁止相同权限码分售不同价格
                throw new ServiceException(ResourceError.MARKET_SALE_TIER_ACTIONS_DUPLICATED);
            }
            marketSaleTiers.add(MarketSaleTierBase.builder().price(marketSaleTier.getPrice()).grantedActionsMask(grantedActionsMask).createAt(LocalDateTime.now()).build());
        }
        marketSaleInfo.setMarketSaleTiers(marketSaleTiers);

        String aclRecalculateReason = null;
        if ((marketSaleInfo.getStatus() == MarketSaleStatus.PUBLISHED || marketSaleInfo.getStatus() == MarketSaleStatus.OFF_SHELF)
                && Objects.equals(oldOfferVersion, request.getOfferVersion())) {
            // 如果此前是已发布或下架状态，且没有改变上架的版本，则直接上架
            aclRecalculateReason = "MARKET_SALE_INFO_PUBLISHED"; // 上架时重算 ACL
            marketSaleInfo.setStatus(MarketSaleStatus.PUBLISHED);
            // 解除限制
            if (resource.getOverrideGrantedActionsMask() != null) resource.getOverrideGrantedActionsMask().remove(marketGroupId);
        } else { // 否则需要审核
            if (marketSaleInfo.getStatus() == MarketSaleStatus.PUBLISHED) {
                aclRecalculateReason = "MARKET_SALE_INFO_OFF_SHELF"; // 如果此前是已发布状态，此操作将导致下架
            }
            marketSaleInfo.setStatus(MarketSaleStatus.PENDING_REVIEW);
            marketSaleInfo.setOfferVersion(request.getOfferVersion());
            // 清空上次审核信息
            marketSaleInfo.setAuditorId(null);
            marketSaleInfo.setAuditMessage(null);
            marketSaleInfo.setAuditAt(null);
            // 在审核前，该资源不能从 MARKET 组获得任何权限
            if (resource.getOverrideGrantedActionsMask() == null) resource.setOverrideGrantedActionsMask(new HashMap<>());
            resource.getOverrideGrantedActionsMask().put(marketGroupId, 0);
        }
        groupBind.setMarketSaleInfo(marketSaleInfo);
        resource.setGroupBinds(groupBinds);
        resourceItemRepository.save(resource);

        if (StringUtils.hasText(aclRecalculateReason)) { // 如果 ACL 重算原因不为空，则触发 ACL 重算，否则不触发
            resourceEventPublisher.publishAclRecalculateEvent(resource.getResourceId(), aclRecalculateReason);
        }

        if (oldOfferVersion != null && !Objects.equals(oldOfferVersion, marketSaleInfo.getOfferVersion())) { // 变更版本时删除旧版本索引
            applicationEventPublisher.publishEvent(new MarketResourceIndexDeleteByVersionEvent(
                    resource.getResourceId(), marketGroupId, oldOfferVersion));
        }
        // 发布版本索引事件
        applicationEventPublisher.publishEvent(new MarketResourceIndexUpsertEvent(
                resource.getResourceId(), marketGroupId));

        log.info("market sale info submitted. resourceId={} marketGroupId={} offerVersion={}",
                resource.getResourceId(), marketGroupId, request.getOfferVersion());
    }

    @Override
    public void offShelfSaleInfo(MarketSaleOffShelfRequest request) {
        ResourceItemEntity resource = resourceService.getResourceEntity(request.getResourceId());
        String marketGroupId = request.getMarketGroupId();

        MarketSaleInfo marketSaleInfo = getmarketSaleInfo(resource, marketGroupId);

        if (marketSaleInfo.getStatus() == MarketSaleStatus.BANNED) { // 被禁的资源不能下架
            throw new ServiceException(ResourceError.CANNOT_REPUBLISH_BANNED_MARKET_SALE);
        } else if(marketSaleInfo.getStatus() != MarketSaleStatus.PUBLISHED) { // 未上架的资源不能下架
            throw new ServiceException(ResourceError.CANNOT_OPERATE_OFF_SHELF_MARKET_SALE);
        }

        marketSaleInfo.setStatus(MarketSaleStatus.OFF_SHELF);
        if (resource.getOverrideGrantedActionsMask() == null) resource.setOverrideGrantedActionsMask(new HashMap<>());
        resource.getOverrideGrantedActionsMask().put(marketGroupId, 0); // 在上架前，该资源不能从 MARKET 组获得任何权限

        resourceItemRepository.save(resource);
        resourceEventPublisher.publishAclRecalculateEvent(resource.getResourceId(), "MARKET_SALE_INFO_OFF_SHELF");
        applicationEventPublisher.publishEvent(new MarketResourceIndexUpsertEvent(
                resource.getResourceId(), marketGroupId));
        log.info("market sale info off-shelved. resourceId={} marketGroupId={}", resource.getResourceId(), marketGroupId);
    }

    @Override
    public void auditSaleInfo(MarketSaleAuditRequest request, String operatorId) {
        // 当拒绝或封禁资源时，必须提供理由
        if ((request.getStatus() == MarketSaleStatus.REJECTED || request.getStatus() == MarketSaleStatus.BANNED)
                && !StringUtils.hasText(request.getAuditMessage())) {
            throw new ServiceException(ResourceError.MARKET_AUDIT_MESSAGE_INVALID);
        }

        ResourceItemEntity resource = resourceService.getResourceEntity(request.getResourceId());
        String marketGroupId = request.getMarketGroupId();

        MarketSaleInfo marketSaleInfo = getmarketSaleInfo(resource, marketGroupId);

        MarketSaleStatus oldMarketSaleStatus = marketSaleInfo.getStatus(); // 旧状态

        // 审计的版本号必须与当前版本号对应（防止在审查时用户更新导致漏检）
        if (!Objects.equals(marketSaleInfo.getOfferVersion(), request.getOfferVersion())) {
            throw new ServiceException(ResourceError.MARKET_AUDIT_VERSION_CONFLICT);
        }

        marketSaleInfo.setStatus(request.getStatus());
        marketSaleInfo.setAuditMessage(request.getAuditMessage());
        marketSaleInfo.setAuditAt(LocalDateTime.now());
        marketSaleInfo.setAuditorId(operatorId);

        if (resource.getOverrideGrantedActionsMask() == null) resource.setOverrideGrantedActionsMask(new HashMap<>());
        if (request.getStatus() == MarketSaleStatus.PUBLISHED) {
            resource.getOverrideGrantedActionsMask().remove(marketGroupId); // 上架，该资源可从 MARKET 组获得权限
        } else {
            resource.getOverrideGrantedActionsMask().put(marketGroupId, 0); // 在上架前，该资源不能从 MARKET 组获得任何权限
        }
        resourceItemRepository.save(resource);

        if (marketSaleInfo.getStatus() == MarketSaleStatus.PUBLISHED && oldMarketSaleStatus != marketSaleInfo.getStatus()) {
            // 如果改变后为发布状态（说明是新上架），则触发 ACL 重算
            resourceEventPublisher.publishAclRecalculateEvent(resource.getResourceId(), "MARKET_SALE_INFO_PUBLISHED");
        } else if(oldMarketSaleStatus == MarketSaleStatus.PUBLISHED && oldMarketSaleStatus != marketSaleInfo.getStatus()) {
            // 如果改变前为发布状态（说明是新下架），则触发 ACL 重算
            resourceEventPublisher.publishAclRecalculateEvent(resource.getResourceId(), "MARKET_SALE_INFO_OFF_SHELF");
        }
        applicationEventPublisher.publishEvent(new MarketResourceIndexUpsertEvent(
                resource.getResourceId(), marketGroupId));

        log.info("market sale info audited. resourceId={} operatorId={} marketGroupId={} status={} offerVersion={}",
                resource.getResourceId(), operatorId, marketGroupId, request.getStatus(), marketSaleInfo.getOfferVersion());
    }

    @Override
    public MarketOrderResponse purchaseResource(MarketPurchaseRequest request, String buyerId) {
        ResourceItemEntity resource = resourceService.getResourceEntity(request.getResourceId());
        String marketGroupId = request.getMarketGroupId();

        MarketSaleInfo marketSaleInfo = getmarketSaleInfo(resource, marketGroupId);

        // 购买的资源必须是上架状态
        if (marketSaleInfo.getStatus() != MarketSaleStatus.PUBLISHED) {
            throw new ServiceException(ResourceError.CANNOT_PURCHASE_OFF_SHELF_MARKET_SALE);
        }

        // 不能自己购买自己的资源
        if (buyerId.equals(resource.getOwnerId())) {
            throw new ServiceException(ResourceError.CANNOT_PURCHASE_OWN_MARKET_SALE);
        }

        // 检查购买的 OfferID 是否存在
        MarketSaleTierBase marketSaleTier = marketSaleInfo.getMarketSaleTiers().stream()
                .filter(item -> request.getOfferId().equals(item.getOfferId()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ResourceError.MARKET_SALE_TIER_NOT_FOUND));

        // 不能重复购买，检查要购买的权限该用户是否本身就完全拥有
        Map<String, Integer> userMasks = marketSaleInfo.getMarketSpecifiedUsersGrantedActionsMask() == null ? new HashMap<>() : marketSaleInfo.getMarketSpecifiedUsersGrantedActionsMask();
        int existingMask = userMasks.getOrDefault(buyerId, 0);

        if ((existingMask & marketSaleTier.getGrantedActionsMask()) == marketSaleTier.getGrantedActionsMask()){
            throw new ServiceException(ResourceError.MARKET_SALE_TIER_GRANT_ALREADY_EXISTS);
        }

        String traceId = IdUtil.fastSimpleUUID();
        Integer paidPrice = marketSaleTier.getPrice();

        List<ResourceAction> offerGrantedActions = ResourceAction.permissionCodeToActions(marketSaleTier.getGrantedActionsMask());
        String billMeta = "%s (%s)".formatted(resource.getResourceId(), offerGrantedActions.stream().map(Enum::name).toList().toString());

        // 请求交易
        remoteWalletService.settleCoinTrade(WalletSettleCoinTradeRequest.builder()
                .traceId(traceId)
                .buyerId(Long.valueOf(buyerId)).sellerId(Long.valueOf(resource.getOwnerId()))
                .price(paidPrice).meta(billMeta)
                .build());

        // 记录交易记录
        MarketOrderEntity order = MarketOrderEntity.builder()
                .traceId(traceId)
                .buyerId(buyerId).sellerId(resource.getOwnerId())
                .marketGroupId(marketGroupId)
                .purchasedResourceId(resource.getResourceId())
                .purchasedOfferVersion(marketSaleInfo.getOfferVersion())
                .buyerGrantedActionsMask(marketSaleTier.getGrantedActionsMask()).buyerPaidPrice(paidPrice).build();
        MarketOrderEntity saved = marketOrderRepository.save(order);

        // 添加权限
        userMasks.put(buyerId, existingMask | marketSaleTier.getGrantedActionsMask());
        marketSaleInfo.setMarketSpecifiedUsersGrantedActionsMask(userMasks);

        resourceItemRepository.save(resource);
        resourceEventPublisher.publishAclRecalculateEvent(resource.getResourceId(), "MARKET_PURCHASE");

        log.info("market order created. orderId={} resourceId={} marketGroupId={} buyerId={} grantedActionsMask={} offerVersion={}",
                saved.getOrderId(), resource.getResourceId(), marketGroupId, buyerId, marketSaleTier.getGrantedActionsMask() , marketSaleInfo.getOfferVersion());
        return BeanUtil.copyProperties(saved, MarketOrderResponse.class);
    }

    @Override
    public PageR<MarketOrderResponse> listOrders(String buyerId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<MarketOrderEntity> entityPage = marketOrderRepository.findByBuyerId(buyerId, pageable);
        PageR<MarketOrderResponse> pageR = new PageR<>(entityPage.getTotalElements(), page, size);

        pageR.addAll(entityPage.getContent().stream()
                .map(entity -> BeanUtil.copyProperties(entity, MarketOrderResponse.class))
                .toList());
        return pageR;
    }

    private MarketSaleInfo getmarketSaleInfo(ResourceItemEntity resource, String marketGroupId) {
        if (resource.getGroupBinds() == null) throw new ServiceException(ResourceError.MARKET_SALE_INFO_NOT_FOUND);

        GroupTagBind groupTagBind = resource.getGroupBinds().stream()
                .filter(bind -> marketGroupId.equals(bind.getGroupId()))
                .findFirst().orElseThrow(() -> new ServiceException(ResourceError.MARKET_SALE_INFO_NOT_FOUND));

        MarketSaleInfo marketSaleInfo = groupTagBind.getMarketSaleInfo();
        if (marketSaleInfo == null || marketSaleInfo.getMarketSaleTiers() == null || marketSaleInfo.getMarketSaleTiers().isEmpty()) {
            throw new ServiceException(ResourceError.MARKET_SALE_INFO_NOT_FOUND);
        }
        return marketSaleInfo;
    }
}
