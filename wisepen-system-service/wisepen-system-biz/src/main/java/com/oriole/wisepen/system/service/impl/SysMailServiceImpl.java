package com.oriole.wisepen.system.service.impl;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.excpetion.SysErrorCode;
import com.oriole.wisepen.system.service.SysMailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮件发送服务实现类
 *
 * @author Heng.Xiong, Jiazheng.Sun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMailServiceImpl implements SysMailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.from.name:WisePen系统}")
    private String fromName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMail(MailSendDTO mailSendDTO) {
        try {

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(mailSendDTO.getToEmail());
            helper.setSubject(mailSendDTO.getSubject());
            helper.setText(mailSendDTO.getContent(), true);

            mailSender.send(message);
            log.info(String.format("Email sent: To=%s, Subject=%s", mailSendDTO.getToEmail(), mailSendDTO.getSubject()));
        } catch (Exception e) {
            String err = String.format("Email sending failed: To=%s, Subject=%s, Err=%s", mailSendDTO.getToEmail(), mailSendDTO.getSubject(), e.getMessage());
            log.error(err, e);
            throw new ServiceException(SysErrorCode.MAIL_SEND_ERROR);
        }
    }
}