package com.oriole.wisepen.resource.listener;

import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.event.MarketResourceIndexDeleteByResourceAndGroupEvent;
import com.oriole.wisepen.resource.event.MarketResourceIndexDeleteByResourceEvent;
import com.oriole.wisepen.resource.event.MarketResourceIndexDeleteByVersionEvent;
import com.oriole.wisepen.resource.event.MarketResourceIndexUpsertEvent;
import com.oriole.wisepen.resource.event.ResourceIndexDeleteEvent;
import com.oriole.wisepen.resource.event.ResourceMetadataIndexUpsertEvent;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceSearchIndexEventListener {

    private final IResourceService resourceService;
    private final ISearchSyncService searchSyncService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleResourceMetadataIndexUpsertEvent(ResourceMetadataIndexUpsertEvent event) {
        try {
            ResourceItemEntity entity = resourceService.getResourceEntity(event.getResourceId());
            searchSyncService.syncResourceMetadata(entity, event.getFields());
            log.debug("resource metadata index upsert event handled. resourceId={}",
                    event.getResourceId());
        } catch (Exception e) {
            log.warn("resource metadata index upsert event handling failed. resourceId={}",
                    event.getResourceId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleResourceIndexDeleteEvent(ResourceIndexDeleteEvent event) {
        try {
            searchSyncService.deleteResourceIndex(event.getResourceId());
            log.debug("resource index delete event handled. resourceId={}",
                    event.getResourceId());
        } catch (Exception e) {
            log.warn("resource index delete event handling failed. resourceId={}",
                    event.getResourceId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMarketResourceIndexUpsertEvent(MarketResourceIndexUpsertEvent event) {
        try {
            ResourceItemEntity entity = resourceService.getResourceEntity(event.getResourceId());
            searchSyncService.syncMarketResourceIndex(entity, event.getMarketGroupId());
            log.debug("market resource index upsert event handled. resourceId={} marketGroupId={}",
                    event.getResourceId(), event.getMarketGroupId());
        } catch (Exception e) {
            log.warn("market resource index upsert event handling failed. resourceId={} marketGroupId={}",
                    event.getResourceId(), event.getMarketGroupId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMarketResourceIndexDeleteByVersionEvent(MarketResourceIndexDeleteByVersionEvent event) {
        try {
            searchSyncService.deleteMarketResourceIndex(
                    event.getResourceId(), event.getMarketGroupId(), event.getOfferVersion());
            log.debug("market resource index delete by version event handled. resourceId={} marketGroupId={} offerVersion={}",
                    event.getResourceId(), event.getMarketGroupId(), event.getOfferVersion());
        } catch (Exception e) {
            log.warn("market resource index delete by version event handling failed. resourceId={} marketGroupId={} offerVersion={}",
                    event.getResourceId(), event.getMarketGroupId(), event.getOfferVersion(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMarketResourceIndexDeleteByResourceEvent(MarketResourceIndexDeleteByResourceEvent event) {
        try {
            searchSyncService.deleteMarketResourceIndexesByResourceId(event.getResourceId());
            log.debug("market resource index delete by resource event handled. resourceId={}",
                    event.getResourceId());
        } catch (Exception e) {
            log.warn("market resource index delete by resource event handling failed. resourceId={}",
                    event.getResourceId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleMarketResourceIndexDeleteByResourceAndGroupEvent(MarketResourceIndexDeleteByResourceAndGroupEvent event) {
        try {
            searchSyncService.deleteMarketResourceIndexesByResourceIdAndMarketGroupId(
                    event.getResourceId(), event.getMarketGroupId());
            log.debug("market resource index delete by resource and group event handled. resourceId={} marketGroupId={}",
                    event.getResourceId(), event.getMarketGroupId());
        } catch (Exception e) {
            log.warn("market resource index delete by resource and group event handling failed. resourceId={} marketGroupId={}",
                    event.getResourceId(), event.getMarketGroupId(), e);
        }
    }
}
