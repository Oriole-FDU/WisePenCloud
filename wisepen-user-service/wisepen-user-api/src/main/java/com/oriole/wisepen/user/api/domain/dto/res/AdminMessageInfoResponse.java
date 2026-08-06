package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.MessageBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminMessageInfoResponse extends MessageBase {
    private Long messageId;
    private Long readCount;
    private LocalDateTime createTime;
}
