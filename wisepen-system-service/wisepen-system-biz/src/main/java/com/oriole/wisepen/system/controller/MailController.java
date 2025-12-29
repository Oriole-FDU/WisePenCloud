package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailResultDTO;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.domain.dto.ResetPasswordMailRequest;
import com.oriole.wisepen.system.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件发送控制器
 *
 * @author Oriole
 */
@RestController
@RequestMapping("/system/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    /**
     * 发送重置密码邮件
     */
    @PostMapping("/send-reset-password")
    public R<MailResultDTO> sendResetPasswordMail(@RequestBody ResetPasswordMailRequest resetMailDTO) {
        return mailService.sendResetPasswordMail(resetMailDTO);
    }

    /**
     * 通用邮件发送
     */
    @PostMapping("/send")
    public R<MailResultDTO> sendMail(@RequestBody MailSendDTO mailSendDTO) {
        return mailService.sendMail(mailSendDTO);
    }
}