package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.service.SysMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 邮件发送控制器
 *
 * @author Xiong.Heng
 */
@RestController
@RequestMapping("/system/mail")
@RequiredArgsConstructor
public class MailController {

    private final SysMailService sysMailService;

    /**
     * 通用邮件发送
     */
    @PostMapping("/send")
    public R<Void> sendMail(@RequestBody MailSendDTO mailSendDTO) {
        return sysMailService.sendMail(mailSendDTO);
    }
}