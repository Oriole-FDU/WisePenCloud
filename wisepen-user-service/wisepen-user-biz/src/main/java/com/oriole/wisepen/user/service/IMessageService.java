package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.domain.dto.res.MessageInfoResponse;

import java.util.List;

public interface IMessageService {

    void publishMessage(MessagePublishRequest req);

    PageR<MessageInfoResponse> listMessages(Long userId, Integer page, Integer size);

    Long getUnreadMessageCount(Long userId);

    void readMessages(Long userId, List<Long> messageIds);

    void readAllMessages(Long userId);

    void removeMessage(Long userId, Long messageId);
}
