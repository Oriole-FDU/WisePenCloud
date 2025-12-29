package com.oriole.wisepen.system.api;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailResultDTO;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.domain.dto.ResetPasswordMailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 邮件发送远程调用接口
 *
 * @author Oriole
 */
@FeignClient(value = "wisepen-system-service", contextId = "remoteMailService")
public interface RemoteMailService {

    /**
     * 发送重置密码邮件
     *
     * @param resetMailDTO 重置密码邮件DTO
     * @return 发送结果
     */
    @PostMapping("/system/mail/send-reset-password")
    R<MailResultDTO> sendResetPasswordMail(@RequestBody ResetPasswordMailRequest resetMailDTO);

    /**
     * 通用邮件发送接口
     *
     * @param mailSendDTO 邮件发送DTO
     * @return 发送结果
     */
    @PostMapping("/system/mail/send")
    R<MailResultDTO> sendMail(@RequestBody MailSendDTO mailSendDTO);
}