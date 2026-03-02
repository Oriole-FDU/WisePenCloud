package com.oriole.wisepen.resource.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ACL 重新计算事件消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclRecalculateMessage implements Serializable {
    private String resourceId;
    private String triggerSource; // 可选：记录触发源（如 TAG_MOVE, TAG_DELETE），方便排查日志
}