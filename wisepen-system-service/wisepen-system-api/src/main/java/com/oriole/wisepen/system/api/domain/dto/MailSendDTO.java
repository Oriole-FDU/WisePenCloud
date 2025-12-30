package com.oriole.wisepen.system.api.domain.dto;

import com.oriole.wisepen.system.api.constant.MailValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = MailValidationMessage.TO_EMAIL_EMPTY)
    @Email(message = MailValidationMessage.TO_EMAIL_INVALID)
    private String toEmail;

    @NotBlank(message = MailValidationMessage.SUBJECT_EMPTY)
    private String subject;

    @NotBlank(message = MailValidationMessage.TEMPLATE_EMPTY)
    private String template;

    private Map<String, Object> templateParams;
}