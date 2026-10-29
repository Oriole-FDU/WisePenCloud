package com.oriole.wisepen.user.service.task;

import com.oriole.wisepen.user.service.IGroupDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupResourceDashboardMetricAggregateTask {

    private final IGroupDashboardService groupDashboardService;

    @Scheduled(cron = "${wisepen.user.dashboard-aggregate.cron:0 */10 * * * ?}")
    public void aggregateGroupResourceDashboardMetrics() {
        try {
            groupDashboardService.aggregateRecentResourceMetrics();
        } catch (Exception e) {
            log.error("group resource dashboard metric aggregate failed", e);
        }
    }
}
