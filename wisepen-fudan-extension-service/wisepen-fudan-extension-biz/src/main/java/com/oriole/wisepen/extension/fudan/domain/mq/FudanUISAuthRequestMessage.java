package com.oriole.wisepen.extension.fudan.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * UIS 身份认证请求 MQ 消息体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FudanUISAuthRequestMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String taskId;
    private String account;
    private String password;
}