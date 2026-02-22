package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.resource.domain.mq.AclRecalculateMessage;
import com.oriole.wisepen.resource.service.IAclEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_ACL_RECALC;

@Slf4j
@Component // 或者 @Service
@RequiredArgsConstructor
public class KafkaAclEventPublisherImpl implements IAclEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishRecalculateEvent(String resourceId, String triggerSource) {
        try {
            AclRecalculateMessage msg = AclRecalculateMessage.builder()
                    .resourceId(resourceId)
                    .triggerSource(triggerSource)
                    .build();

            // 封装发信逻辑，这里可以统一加 TraceId、发信日志等
            kafkaTemplate.send(TOPIC_ACL_RECALC, resourceId, msg);
            log.debug("成功发布 ACL 重算事件, ResourceId: {}", resourceId);
        } catch (Exception e) {
            log.error("发布 ACL 重算事件失败, ResourceId: {}", resourceId, e);
        }
    }
}