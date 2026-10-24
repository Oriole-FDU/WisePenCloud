package com.oriole.wisepen.user.api.domain.dto.req;

import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.user.api.enums.MessageDeliveryScope;
import com.oriole.wisepen.user.api.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePublishRequest {
    private List<Long> receiverUserIds;
    private MessageDeliveryScope deliveryScope;
    private MessageType messageType;

    @NotBlank(message = "消息标题不能为空")
    private String title;

    @NotBlank(message = "消息内容不能为空")
    private String content;
    private String jumpUrl;
    private BusinessDomain sourceService;
    private String bizTraceId;
    private String extra;

    @JsonIgnore
    public String getMessageHash() {
        return SecureUtil.sha256(String.join("\u001F",
                hashField(sourceService),
                hashField(messageType),
                hashField(title),
                hashField(content),
                hashField(jumpUrl),
                hashField(extra)));
    }

    private static String hashField(Object value) {
        if (value == null) {
            return "-1:";
        }
        String text = value.toString();
        return text.length() + ":" + text;
    }
}
