package com.oriole.wisepen.system.api.domain.dto;

import com.oriole.wisepen.system.api.constant.MailValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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

    @NotBlank(message = MailValidationMessage.CONTENT_EMPTY)
    private String content;
}