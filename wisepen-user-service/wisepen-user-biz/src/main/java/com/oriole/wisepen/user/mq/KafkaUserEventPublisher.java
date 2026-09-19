package com.oriole.wisepen.user.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.mq.ReliablePublisher;
import com.oriole.wisepen.extension.fudan.domain.mq.FudanUISAuthRequestMessage;
import com.oriole.wisepen.user.api.domain.mq.UserTaskCompleteMessage;
import com.oriole.wisepen.user.exception.UserError;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.oriole.wisepen.extension.fudan.constant.MqTopicConstants.FUDAN_UIS_AUTH_REQ;
import static com.oriole.wisepen.user.api.constant.MqTopicConstants.TOPIC_USER_TASK_COMPLETE;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaUserEventPublisher {

    @Resource
    ReliablePublisher reliablePublisher;

    private final ObjectMapper objectMapper;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = FUDAN_UIS_AUTH_REQ,
            description = "用户发起复旦 UIS 认证后发布认证请求，由复旦扩展服务异步抓取认证状态。",
            payloadType = FudanUISAuthRequestMessage.class,
            message = @AsyncMessage(name = "FudanUISAuthRequestMessage", title = "复旦 UIS 认证请求")
    ))
    public void publishUisAuthRequest(Long userId, FudanUISAuthRequestMessage message) {
        try {
            reliablePublisher.publish(FUDAN_UIS_AUTH_REQ, null, objectMapper.writeValueAsString(message), null);
            log.debug("fudan uis auth publish requested. topic={} userId={}",
                    FUDAN_UIS_AUTH_REQ, userId);
        } catch (Exception e) {
            log.error("fudan uis auth publish request failed. topic={} userId={}",
                    FUDAN_UIS_AUTH_REQ, userId, e);
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_REQUEST_FAILED);
        }
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_USER_TASK_COMPLETE,
            description = "发布用户任务完成请求，由用户服务异步写入任务记录并发放奖励。",
            payloadType = UserTaskCompleteMessage.class,
            message = @AsyncMessage(name = "UserTaskCompleteMessage", title = "用户任务完成请求")
    ))
    public void publishUserTaskComplete(UserTaskCompleteMessage message) {
        try {
            String userId = message.getUserId() == null ? null : String.valueOf(message.getUserId());
            reliablePublisher.publish(TOPIC_USER_TASK_COMPLETE, userId, message, UUID.randomUUID().toString());
            log.debug("user task complete publish requested. topic={} userId={} taskCode={}",
                    TOPIC_USER_TASK_COMPLETE, message.getUserId(), message.getTaskCode());
        } catch (Exception e) {
            log.error("user task complete publish request failed. topic={} userId={} taskCode={}",
                    TOPIC_USER_TASK_COMPLETE, message.getUserId(), message.getTaskCode(), e);
        }
    }
}
