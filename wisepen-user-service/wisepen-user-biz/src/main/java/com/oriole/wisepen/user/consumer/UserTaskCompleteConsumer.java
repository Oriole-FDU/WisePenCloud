package com.oriole.wisepen.user.consumer;

import com.oriole.wisepen.user.api.domain.mq.UserTaskCompleteMessage;
import com.oriole.wisepen.user.service.IUserTaskService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.user.api.constant.MqTopicConstants.TOPIC_USER_TASK_COMPLETE;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserTaskCompleteConsumer {

    private final IUserTaskService userTaskService;

    @KafkaListener(topics = TOPIC_USER_TASK_COMPLETE, groupId = "wisepen-user-task-complete-group",
            properties = {
                    "spring.json.use.type.headers:false",
                    "spring.json.value.default.type:com.oriole.wisepen.user.api.domain.mq.UserTaskCompleteMessage"
            }
    )
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_USER_TASK_COMPLETE,
            description = "消费用户任务完成请求，按任务处理器规则写入任务记录并发放奖励。",
            payloadType = UserTaskCompleteMessage.class,
            message = @AsyncMessage(name = "UserTaskCompleteMessage", title = "用户任务完成请求")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-user-task-complete-group")
    public void onUserTaskComplete(UserTaskCompleteMessage message) {
        log.info("user task complete event received. topic={} userId={} taskCode={}",
                TOPIC_USER_TASK_COMPLETE, message.getUserId(), message.getTaskCode());
        try {
            userTaskService.complete(
                    message.getUserId(),
                    message.getTaskCode(),
                    message.getOperatorId(),
                    message.getMeta()
            );
            log.debug("user task complete event consumed. topic={} userId={} taskCode={}",
                    TOPIC_USER_TASK_COMPLETE, message.getUserId(), message.getTaskCode());
        } catch (Exception e) {
            log.error("user task complete event consumption failed. topic={} userId={} taskCode={}",
                    TOPIC_USER_TASK_COMPLETE, message.getUserId(), message.getTaskCode(), e);
            throw e;
        }
    }
}
