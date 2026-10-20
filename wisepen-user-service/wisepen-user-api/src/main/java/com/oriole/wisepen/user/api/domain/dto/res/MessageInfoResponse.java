package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.MessageBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageInfoResponse extends MessageBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "消息 ID")
    private Long messageId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "阅读时间")
    private LocalDateTime readTime;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "number", format = "int64", description = "创建时间")
    private LocalDateTime createTime;
}
