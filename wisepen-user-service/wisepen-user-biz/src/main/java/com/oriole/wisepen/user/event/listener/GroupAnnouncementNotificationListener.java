package com.oriole.wisepen.user.event.listener;

import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.enums.MessageDeliveryScope;
import com.oriole.wisepen.user.api.enums.MessageType;
import com.oriole.wisepen.user.event.GroupAnnouncementNotificationEvent;
import com.oriole.wisepen.user.service.IMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupAnnouncementNotificationListener {

    private final IMessageService messageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleGroupAnnouncementNotification(GroupAnnouncementNotificationEvent event) {
        if (event.getReceiverUserIds().isEmpty()) {
            return;
        }
        try {
            MessagePublishRequest request = new MessagePublishRequest();
            request.setReceiverUserIds(event.getReceiverUserIds());
            request.setDeliveryScope(MessageDeliveryScope.DIRECT);
            request.setMessageType(MessageType.GROUP);
            request.setTitle(event.getTitle());
            request.setContent(event.getContent());
            request.setJumpUrl("/group/announcement/getAnnouncementDetail?groupId=%d&announcementId=%d"
                    .formatted(event.getGroupId(), event.getAnnouncementId()));
            request.setSourceService(BusinessDomain.USER);
            request.setBizTraceId(event.getBizTraceId());
            messageService.publishMessage(request);
        } catch (Exception e) {
            log.warn("group announcement notification degraded. groupId={} announcementId={}",
                    event.getGroupId(), event.getAnnouncementId(), e);
        }
    }
}
