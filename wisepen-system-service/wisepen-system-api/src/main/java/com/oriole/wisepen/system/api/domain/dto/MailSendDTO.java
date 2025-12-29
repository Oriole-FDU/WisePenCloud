package com.oriole.wisepen.system.api.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 邮件发送DTO
 *
 * @author Oriole
 */
@Data
public class MailSendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 收件人邮箱
     */
    @NotBlank(message = "收件人邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String toEmail;

    /**
     * 邮件主题
     */
    @NotBlank(message = "邮件主题不能为空")
    private String subject;

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    private String template;

    /**
     * 模板参数
     */
    private Map<String, Object> templateParams;
}