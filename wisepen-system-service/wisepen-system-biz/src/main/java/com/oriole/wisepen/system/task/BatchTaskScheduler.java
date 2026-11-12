package com.oriole.wisepen.system.task;

import com.oriole.wisepen.system.service.BatchTaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchTaskScheduler {

    private final BatchTaskExecutionService batchTaskExecutionService;

    @Scheduled(fixedDelayString = "${wisepen.batch-task.schedule.poll-delay-ms:10000}")
    public void executePendingBatchTasks() {
        long start = System.currentTimeMillis();
        log.info("batch task execution started. task=batchTaskExecution");
        try {
            BatchTaskExecutionService.ExecutionSummary summary = batchTaskExecutionService.executePendingItems();
            log.info("batch task execution finished. task=batchTaskExecution processed={} succeeded={} failed={} interrupted={} costMs={}",
                    summary.processed(), summary.succeeded(), summary.failed(), summary.interrupted(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("batch task execution aborted. task=batchTaskExecution", e);
        }
    }
}
