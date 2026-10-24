package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.MessageDeliveryScope;
import com.oriole.wisepen.user.api.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class MessageBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "消息投递范围")
    private MessageDeliveryScope deliveryScope;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "消息类型")
    private MessageType messageType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "消息标题")
    private String title;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "消息正文")
    private String content;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "跳转地址")
    private String jumpUrl;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "消息扩展信息")
    private String extra;
}
