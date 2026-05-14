package com.oriole.wisepen.resource.mq;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.mq.AclRecalculateMessage;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import com.oriole.wisepen.resource.util.ESIndexBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_ACL_RECALC;

/**
 * 搜索域 ACL 重算消费者。
 * <p>
 * 与 {@link AclRecalculateConsumer} 共享同一 topic 但使用独立 groupId，保证两个消费者各自独立
 * commit offset、互不抢分区。延迟 3 秒反查的目的：先让资源域 ACL 重算事务落库写回
 * {@code computedGroupAcls}，再去读最新数据，避免竞态。
 * <p>
 * 元数据 + ACL 的翻译统一交给 {@link ESIndexBuilder}，本类只负责接消息 / 调度 / 触发 Upsert。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ESAclRecalcConsumer {

    private static final long DELAY_SECONDS = 3L;

    private final ESIndexBuilder ESIndexBuilder;
    private final ISearchSyncService searchSyncService;
    private final ScheduledExecutorService scheduler;

    @KafkaListener(topics = TOPIC_ACL_RECALC, groupId = "wisepen-resource-es-acl-recalc-group")
    public void onAclRecalculate(AclRecalculateMessage message) {
        if (message == null || StrUtil.isBlank(message.getResourceId())) {
            return;
        }
        String resourceId = message.getResourceId();
        log.info("searchAclRecalc received topic={} resourceId={} trigger={}",
                TOPIC_ACL_RECALC, resourceId, message.getTriggerSource());
        scheduler.schedule(() -> upsertResourceMetaData(resourceId), DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /** 同进程拉取最新元数据 + 通过位掩码翻译出 allowedUsers / groupAcls，仅写"元数据 + ACL"维度 */
    private void upsertResourceMetaData(String resourceId) {
        try {
            Optional<ESIndexEntity> entityOpt = ESIndexBuilder.build(resourceId, null);
            if (entityOpt.isEmpty()) {
                log.warn("searchAclRecalc skipped reason=resource-not-found resourceId={}", resourceId);
                return;
            }
            ESIndexEntity entity = entityOpt.get();
            searchSyncService.upsertIndexMetaData(entity);
            log.info("searchAclRecalc upserted resourceId={} resourceName={} esId={} allowedUsers={} groupAcls={} tags={}",
                    resourceId, entity.getResourceName(),entity.getId(),
                    entity.getAllowedUsers() == null ? 0 : entity.getAllowedUsers().size(),
                    entity.getGroupAcls() == null ? 0 : entity.getGroupAcls().size(),
                    entity.getTags() == null ? 0 : entity.getTags().size());
        } catch (Exception e) {
            log.error("searchAclRecalc failed resourceId={}", resourceId, e);
        }
    }
}
