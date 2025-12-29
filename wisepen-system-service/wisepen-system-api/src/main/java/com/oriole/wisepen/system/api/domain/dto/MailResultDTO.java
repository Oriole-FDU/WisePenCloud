package com.oriole.wisepen.system.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邮件发送结果DTO
 *
 * @author Oriole
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮件ID
     */
    private Long mailId;

    /**
     * 是否发送成功
     */
    private Boolean success;

    /**
     * 错误信息（如果发送失败）
     */
    private String errorMessage;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;
}