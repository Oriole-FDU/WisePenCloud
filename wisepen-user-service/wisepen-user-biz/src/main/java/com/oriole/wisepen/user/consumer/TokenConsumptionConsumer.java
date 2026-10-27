package com.oriole.wisepen.user.consumer;

import com.oriole.wisepen.user.api.domain.mq.TokenConsumptionMessage;
import com.oriole.wisepen.user.service.IWalletService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.user.api.constant.MqTopicConstants.TOPIC_TOKEN_CONSUMPTION;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenConsumptionConsumer {

	private final IWalletService walletService;

	@KafkaListener(topics = TOPIC_TOKEN_CONSUMPTION, groupId = "wisepen-user-token-consumption-group",
			properties = {
					"spring.json.use.type.headers:false",
					"spring.json.value.default.type:com.oriole.wisepen.user.api.domain.mq.TokenConsumptionMessage"
			}
	)
	@AsyncListener(operation = @AsyncOperation(
			channelName = TOPIC_TOKEN_CONSUMPTION,
			description = "消费 Token 扣费事件，按首选小组、用户计费顺序和资源审批解析付款组，再完成小组优先、个人兜底的 Token 消耗入账。",
			payloadType = TokenConsumptionMessage.class,
			message = @AsyncMessage(name = "TokenConsumptionMessage", title = "Token 扣费事件")
	))
	@KafkaAsyncOperationBinding(groupId = "wisepen-user-token-consumption-group")
	public void onTokenConsumption(TokenConsumptionMessage message) {
		log.info("token consumption event received. topic={} traceId={}",
				TOPIC_TOKEN_CONSUMPTION, message.getTraceId());
		try {
			walletService.consumptionToken(message);
			log.debug("token consumption event consumed. topic={} traceId={}",
					TOPIC_TOKEN_CONSUMPTION, message.getTraceId());
		} catch (Exception e) {
			log.error("token consumption event consumption failed. topic={} traceId={}",
					TOPIC_TOKEN_CONSUMPTION, message.getTraceId(), e);
			throw e;
		}
	}
}
