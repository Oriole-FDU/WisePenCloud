package com.oriole.wisepen.system.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.config.SystemProperties;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.service.SysMailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
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
    private final MailProperties mailProperties;
    private final SystemProperties systemProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMail(MailSendDTO mailSendDTO) {
        try {

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, mailProperties.getDefaultEncoding().name());

            helper.setFrom(mailProperties.getUsername(), systemProperties.getMail().getFromName());
            helper.setTo(mailSendDTO.getToEmail());
            helper.setSubject(mailSendDTO.getSubject());
            helper.setText(mailSendDTO.getContent(), true);

            mailSender.send(message);
            log.info("mail sent. toEmail={} subject={}",
                    mailSendDTO.getToEmail(), mailSendDTO.getSubject());
        } catch (Exception e) {
            log.error("mail send failed. toEmail={} subject={}",
                    mailSendDTO.getToEmail(), mailSendDTO.getSubject(), e);
            throw new ServiceException(SysError.MAIL_SEND_FAILED);
        }
    }
}
