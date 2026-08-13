package com.oriole.wisepen.resource.task;

import com.oriole.wisepen.resource.domain.entity.MarketOrderEntity;
import com.oriole.wisepen.resource.enums.MarketOrderStatus;
import com.oriole.wisepen.resource.repository.MarketOrderRepository;
import com.oriole.wisepen.resource.service.IMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketOrderRecoveryTask {

    private final MarketOrderRepository marketOrderRepository;
    private final IMarketService marketService;

    @Scheduled(fixedDelayString = "${wisepen.resource.market-order-recovery-delay-ms:60000}")
    public void recoverPendingOrders() {
        long startMs = System.currentTimeMillis();
        int processed = 0;
        int failed = 0;
        log.info("market order recovery started. task=marketOrder");

        try {
            List<MarketOrderEntity> pendingOrders = marketOrderRepository
                    .findTop100ByStatusOrderByUpdateTimeAsc(MarketOrderStatus.PENDING);
            for (MarketOrderEntity order : pendingOrders) {
                try {
                    marketService.recoverPendingOrder(order.getOrderId());
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("market order recovery failed. orderId={} resourceId={}",
                            order.getOrderId(), order.getPurchasedResourceId(), e);
                }
            }
        } catch (Exception e) {
            log.error("market order recovery aborted. task=marketOrder processed={} failed={} costMs={}",
                    processed, failed, System.currentTimeMillis() - startMs, e);
            return;
        }

        log.info("market order recovery finished. task=marketOrder processed={} failed={} costMs={}",
                processed, failed, System.currentTimeMillis() - startMs);
    }
}
