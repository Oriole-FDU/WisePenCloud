package com.oriole.wisepen.system.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 邮件发送远程调用接口
 *
 * @author Heng.Xiong
 */
@FeignClient(value = "wisepen-system-service", contextId = "remoteMailService")
public interface RemoteMailService {
    /**
     * 邮件发送接口
     *
     * @param mailSendDTO 邮件发送DTO
     * @return 发送结果
     */
    @PostMapping("/system/mail/send")
    R<Void> sendMail(@RequestBody MailSendDTO mailSendDTO);
}