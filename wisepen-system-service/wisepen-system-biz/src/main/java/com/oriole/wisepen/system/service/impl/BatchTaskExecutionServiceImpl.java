package com.oriole.wisepen.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import com.oriole.wisepen.system.api.enums.BatchTaskStatus;
import com.oriole.wisepen.system.config.BatchTaskProperties;
import com.oriole.wisepen.system.domain.entity.BatchTaskEntity;
import com.oriole.wisepen.system.domain.entity.BatchTaskItemEntity;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.mapper.BatchTaskItemMapper;
import com.oriole.wisepen.system.mapper.BatchTaskMapper;
import com.oriole.wisepen.system.service.BatchTaskExecutionService;
import com.oriole.wisepen.system.service.batch.BatchTaskExecutorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTaskExecutionServiceImpl implements BatchTaskExecutionService {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final BatchTaskMapper batchTaskMapper;
    private final BatchTaskItemMapper batchTaskItemMapper;
    private final BatchTaskExecutorRegistry executorRegistry;
    private final BatchTaskProperties batchTaskProperties;

    @Override
    public ExecutionSummary executePendingItems() {
        Set<Long> affectedTaskIds = new LinkedHashSet<>();
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        int interrupted = 0;

        LocalDateTime now = LocalDateTime.now();
        // 检查并回收超时的执行中任务项
        BatchTaskProperties.Execution executionProperties = batchTaskProperties.getExecution();
        List<BatchTaskItemEntity> staleItems = batchTaskItemMapper.selectStaleRunningItems(
                now.minusMinutes(executionProperties.getStaleMinutes()), executionProperties.getBatchSize());
        for (BatchTaskItemEntity item : staleItems) {
            int updated = batchTaskItemMapper.completeRunningItem(
                    item.getId(), BatchTaskItemStatus.FAILED, null,
                    SysError.BATCH_TASK_ITEM_INTERRUPTED.getCode(), SysError.BATCH_TASK_ITEM_INTERRUPTED.getMsg(), now
            );
            if (updated > 0) {
                interrupted++;
                affectedTaskIds.add(item.getTaskId()); // 统计受影响的任务
            }
        }

        List<BatchTaskItemEntity> candidates = batchTaskItemMapper.selectRunnableItems(executionProperties.getBatchSize());

        Map<Long, BatchTaskEntity> taskCache = new HashMap<>();
        for (BatchTaskItemEntity item : candidates) {

            LocalDateTime startTime = LocalDateTime.now();
            // claimPendingItem 会尝试把数据库中的任务项从 PENDING 原子地更新为 RUNNING，并返回受影响的行数
            // 没有更新到行时说明它目前不能被领取，跳到循环的下一项
            if (batchTaskItemMapper.claimPendingItem(item.getId(), startTime) == 0) continue;

            affectedTaskIds.add(item.getTaskId());
            item.setStatus(BatchTaskItemStatus.RUNNING);
            item.setStartTime(startTime);
            item.setUpdateTime(startTime);

            // 尝试获取父任务
            BatchTaskEntity task = taskCache.computeIfAbsent(item.getTaskId(), batchTaskMapper::selectById);
            boolean active = task != null
                    && (task.getStatus() == BatchTaskStatus.PENDING || task.getStatus() == BatchTaskStatus.RUNNING);

            // 父任务状态为 PENDING 时
            if (task != null && task.getStatus() == BatchTaskStatus.PENDING) {
                // 更新父任务状态
                int updated = batchTaskMapper.update(null, Wrappers.<BatchTaskEntity>lambdaUpdate()
                        .eq(BatchTaskEntity::getId, task.getId())
                        .eq(BatchTaskEntity::getStatus, BatchTaskStatus.PENDING)
                        .isNull(BatchTaskEntity::getDeletedAt)
                        .set(BatchTaskEntity::getStatus, BatchTaskStatus.RUNNING)
                        .set(BatchTaskEntity::getStartTime, startTime)
                        .set(BatchTaskEntity::getUpdateTime, startTime));
                if (updated > 0) {
                    task.setStatus(BatchTaskStatus.RUNNING);
                } else {
                    // 不成功，重新选择并更新缓存
                    task = batchTaskMapper.selectById(task.getId());
                    taskCache.put(item.getTaskId(), task);
                    active = task != null && task.getStatus() == BatchTaskStatus.RUNNING;
                }
            }

            // 父任务状态为 PAUSING 或 PAUSED 时
            if (task != null && (task.getStatus() == BatchTaskStatus.PAUSING
                    || task.getStatus() == BatchTaskStatus.PAUSED)) {
                // 尚未调用执行器的任务项释放回待处理状态
                batchTaskItemMapper.releaseRunningItem(item.getId(), LocalDateTime.now());
                continue; // 跳过后续处理
            }

            processed++;
            if (!active) {
                // 任务不在可运行的状态
                item.setStatus(BatchTaskItemStatus.FAILED);
                item.setErrorCode(SysError.BATCH_TASK_STATE_INVALID.getCode());
                item.setErrorMessage(SysError.BATCH_TASK_STATE_INVALID.getMsg());
            } else {
                try {
                    // 开始执行
                    BatchTaskItemEntity executedItem = executorRegistry.get(task.getType()).executeItem(item);
                    if (executedItem == null) throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED);
                    // 记录执行结果（成功）
                    item.setResult(executedItem.getResult());
                    item.setStatus(BatchTaskItemStatus.SUCCEEDED);
                    item.setErrorCode(null);
                    item.setErrorMessage(null);
                } catch (Exception e) {
                    // 记录执行结果（失败）
                    item.setStatus(BatchTaskItemStatus.FAILED);
                    if (e instanceof ServiceException serviceException) {
                        item.setErrorCode(serviceException.getErrorResult().getCode());
                        item.setErrorMessage(serviceException.getMessage());
                    } else {
                        item.setErrorCode(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED.getCode());
                        item.setErrorMessage(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED.getMsg());
                    }
                    log.warn("batch task item failed. taskId={} itemId={} type={}",
                            task.getId(), item.getId(), task.getType(), e);
                }
            }

            // 截断过长的异常
            if (item.getErrorMessage() != null
                    && item.getErrorMessage().length() > ERROR_MESSAGE_MAX_LENGTH) {
                item.setErrorMessage(item.getErrorMessage().substring(0, ERROR_MESSAGE_MAX_LENGTH));
            }

            LocalDateTime finishTime = LocalDateTime.now();
            item.setFinishTime(finishTime);
            item.setUpdateTime(finishTime);

            // 仅允许 RUNNING 项完成，避免迟到的执行结果覆盖回收或其他实例写入的状态
            if (batchTaskItemMapper.completeRunningItem(
                    item.getId(), item.getStatus(), item.getResult(),
                    item.getErrorCode(), item.getErrorMessage(), finishTime) > 0) {
                if (item.getStatus() == BatchTaskItemStatus.SUCCEEDED) succeeded++;
                else failed++;
            }
        }

        affectedTaskIds.forEach(this::reconcileTaskStatus);
        return new ExecutionSummary(processed, succeeded, failed, interrupted);
    }

    @Override
    public void reconcileTaskStatus(Long taskId) {
        BatchTaskEntity task = batchTaskMapper.selectOne(Wrappers.<BatchTaskEntity>lambdaQuery()
                .eq(BatchTaskEntity::getId, taskId)
                .isNull(BatchTaskEntity::getDeletedAt));
        if (task == null) return;

        BatchTaskStatus currentStatus = task.getStatus();
        boolean active = currentStatus == BatchTaskStatus.PENDING || currentStatus == BatchTaskStatus.RUNNING;
        if (!active && currentStatus != BatchTaskStatus.PAUSING) return;

        // 只有父任务的所有任务项都离开 PENDING/RUNNING 后，才汇总并结束父任务
        long pending = batchTaskItemMapper.selectCount(Wrappers.<BatchTaskItemEntity>lambdaQuery()
                .eq(BatchTaskItemEntity::getTaskId, taskId)
                .eq(BatchTaskItemEntity::getStatus, BatchTaskItemStatus.PENDING));
        long running = batchTaskItemMapper.selectCount(Wrappers.<BatchTaskItemEntity>lambdaQuery()
                .eq(BatchTaskItemEntity::getTaskId, taskId)
                .eq(BatchTaskItemEntity::getStatus, BatchTaskItemStatus.RUNNING));

        if (currentStatus == BatchTaskStatus.PAUSING) {
            if (running > 0) return;
            if (pending > 0) {
                // 没有在 RUNNING的任务了，说明成功停止
                LocalDateTime now = LocalDateTime.now();
                // 将 PAUSING 转为 PAUSED
                int updated = batchTaskMapper.update(null, Wrappers.<BatchTaskEntity>lambdaUpdate()
                        .eq(BatchTaskEntity::getId, taskId)
                        .eq(BatchTaskEntity::getStatus, BatchTaskStatus.PAUSING)
                        .isNull(BatchTaskEntity::getDeletedAt)
                        .set(BatchTaskEntity::getStatus, BatchTaskStatus.PAUSED)
                        .set(BatchTaskEntity::getUpdateTime, now));
                if (updated > 0) {
                    log.info("batch task status changed. taskId={} from={} to={}",
                            taskId, BatchTaskStatus.PAUSING, BatchTaskStatus.PAUSED);
                }
                return;
            }
        } else if (pending > 0 || running > 0) {
            return;
        }

        long succeeded = batchTaskItemMapper.selectCount(Wrappers.<BatchTaskItemEntity>lambdaQuery()
                .eq(BatchTaskItemEntity::getTaskId, taskId)
                .eq(BatchTaskItemEntity::getStatus, BatchTaskItemStatus.SUCCEEDED));
        long failed = batchTaskItemMapper.selectCount(Wrappers.<BatchTaskItemEntity>lambdaQuery()
                .eq(BatchTaskItemEntity::getTaskId, taskId)
                .eq(BatchTaskItemEntity::getStatus, BatchTaskItemStatus.FAILED));
        BatchTaskStatus finalStatus = succeeded == 0
                ? BatchTaskStatus.FAILED
                : failed == 0 ? BatchTaskStatus.SUCCEEDED : BatchTaskStatus.PARTIAL_SUCCESS;
        LocalDateTime finishTime = LocalDateTime.now();
        int updated = batchTaskMapper.update(null, Wrappers.<BatchTaskEntity>lambdaUpdate()
                .eq(BatchTaskEntity::getId, taskId)
                .eq(BatchTaskEntity::getStatus, currentStatus)
                .isNull(BatchTaskEntity::getDeletedAt)
                .set(BatchTaskEntity::getStatus, finalStatus)
                .set(BatchTaskEntity::getFinishTime, finishTime)
                .set(BatchTaskEntity::getUpdateTime, finishTime));
        if (updated > 0) {
            log.info("batch task status changed. taskId={} from={} to={} succeeded={} failed={}",
                    taskId, currentStatus, finalStatus, succeeded, failed);
        }
    }

}
