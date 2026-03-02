package com.oriole.wisepen.system.service.impl;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.system.api.domain.dto.MailResultDTO;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 邮件发送服务实现类
 *
 * @author Oriole
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.from.name:WisePen系统}")
    private String fromName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<MailResultDTO> sendMail(MailSendDTO mailSendDTO) {
        try {
            sendMail(mailSendDTO.getToEmail(), mailSendDTO.getSubject(), mailSendDTO.getContent());

            log.info("邮件发送成功：收件人={}, 主题={}", mailSendDTO.getToEmail(), mailSendDTO.getSubject());

            // 返回结果
            MailResultDTO result = new MailResultDTO();
            result.setSuccess(true);
            result.setSendTime(LocalDateTime.now());
            return R.ok(result);

        } catch (Exception e) {
            log.error("发送邮件失败：收件人={}, 主题={}, 错误={}",
                    mailSendDTO.getToEmail(), mailSendDTO.getSubject(), e.getMessage(), e);

            // 返回错误结果
            MailResultDTO result = new MailResultDTO();
            result.setSuccess(false);
            result.setErrorMessage("邮件发送失败: " + e.getMessage());
            return R.ok(result); // 返回成功响应，但内部标记为失败
        }
    }

    /**
     * 发送邮件的基础方法
     */
    private void sendMail(String to, String subject, String content) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // 使用从Nacos配置中加载的username作为发件人地址
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);

        log.info("当前使用的发件人账号: {}", fromEmail);
        mailSender.send(message);

    }
}