package com.oriole.wisepen.system.service;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailResultDTO;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.domain.dto.ResetPasswordMailRequest;

/**
 * 邮件发送服务接口
 *
 * @author Oriole
 */
public interface MailService {

    /**
     * 发送重置密码邮件
     *
     * @param resetMailDTO 重置密码邮件DTO
     * @return 发送结果
     */
    R<MailResultDTO> sendResetPasswordMail(ResetPasswordMailRequest resetMailDTO);

    /**
     * 通用邮件发送
     *
     * @param mailSendDTO 邮件发送DTO
     * @return 发送结果
     */
    R<MailResultDTO> sendMail(MailSendDTO mailSendDTO);
}