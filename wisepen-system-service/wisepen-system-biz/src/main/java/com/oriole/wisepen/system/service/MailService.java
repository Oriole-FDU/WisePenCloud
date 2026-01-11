package com.oriole.wisepen.system.service;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailResultDTO;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;

/**
 * 邮件发送服务接口
 *
 * @author Oriole
 */
public interface MailService {
    R<MailResultDTO> sendMail(MailSendDTO mailSendDTO);
}