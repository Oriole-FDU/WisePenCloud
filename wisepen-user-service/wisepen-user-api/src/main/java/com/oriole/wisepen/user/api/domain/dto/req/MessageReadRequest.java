package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.MessageValidationMsg;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MessageReadRequest {
    @NotEmpty(message = MessageValidationMsg.MESSAGE_IDS_EMPTY)
    private List<Long> messageIds;
}
