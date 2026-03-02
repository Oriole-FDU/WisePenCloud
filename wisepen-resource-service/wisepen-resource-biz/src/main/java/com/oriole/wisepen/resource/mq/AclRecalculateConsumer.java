package com.oriole.wisepen.resource.mq;

import com.oriole.wisepen.resource.domain.mq.AclRecalculateMessage;
import com.oriole.wisepen.resource.service.IResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_ACL_RECALC;

@Slf4j
@Component
@RequiredArgsConstructor
public class AclRecalculateConsumer {

    private final IResourceService resourceService;

    @KafkaListener(topics = TOPIC_ACL_RECALC, groupId = "wisepen-resource-acl-recalc-group")
    public void onAclRecalculate(AclRecalculateMessage message) {
        try {
            log.debug("接收到 ACL 重算事件, ResourceId: {}", message.getResourceId());
            // 执行真正的溯源和权限计算
            resourceService.calculateResourceAcl(message.getResourceId());
        } catch (Exception e) {
            // 这里建议捕获异常，防止某一个资源的脏数据导致消费者无限重试和阻塞
            // 在成熟的架构中，可以将失败的 message 发送到 Dead Letter Queue (死信队列)
            log.error("ACL 重算失败, ResourceId: {}", message.getResourceId(), e);
        }
    }
}