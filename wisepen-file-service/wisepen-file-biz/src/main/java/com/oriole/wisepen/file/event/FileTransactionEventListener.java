package com.oriole.wisepen.file.event;

import com.alibaba.fastjson2.JSON;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.event.FileUploadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 事务事件监听器
 * 负责在文件入库事务成功提交后，推送任务到 Redis 队列
 *
 * @author Ian.Xiong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileTransactionEventListener {

    private final StringRedisTemplate stringRedisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFileUploadEvent(FileUploadEvent event) {
        log.info("Transaction committed. Processing FileUploadEvent for fileId: {}", event.getFileId());

        if (event.isNewFile()) {
            // 3.1 推送上传任务
            if (event.getUploadTask() != null) {
                stringRedisTemplate.opsForList().leftPush(FileConstants.UPLOAD_QUEUE_KEY, JSON.toJSONString(event.getUploadTask()));
                log.info("Pushed upload task to Redis for fileId: {}", event.getFileId());
            }

            // 3.2 推送转换任务 (仅新文件中的 Office 文档)
            if (event.isOffice() && event.getConvertTask() != null) {
                stringRedisTemplate.opsForList().leftPush(FileConstants.CONVERT_QUEUE_KEY, JSON.toJSONString(event.getConvertTask()));
                log.info("Pushed conversion task to Redis for fileId: {}", event.getFileId());
            }
        }
    }
}
