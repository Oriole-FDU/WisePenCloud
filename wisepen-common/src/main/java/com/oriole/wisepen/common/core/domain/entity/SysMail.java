package com.oriole.wisepen.common.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysMail {
    private Long id;
    private String recipient;
    private String subject;
    private String content;
    private String template;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime sendTime;
}