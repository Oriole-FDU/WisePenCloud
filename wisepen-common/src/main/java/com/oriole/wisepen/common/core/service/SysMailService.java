package com.oriole.wisepen.common.core.service;

import com.oriole.wisepen.common.core.domain.entity.SysMail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class SysMailService {

    private static JavaMailSender staticMailSender;
    private static TemplateEngine staticTemplateEngine;
    private static String staticFromEmail;
    private static String staticFromName;

    @Autowired(required = false)
    public void setMailSender(JavaMailSender mailSender) {
        staticMailSender = mailSender;
        log.info("JavaMailSender 已注入");
    }

    @Autowired(required = false)
    public void setTemplateEngine(TemplateEngine templateEngine) {
        staticTemplateEngine = templateEngine;
        log.info("TemplateEngine 已注入");
    }

    @Value("${spring.mail.username:}")
    private void setFromEmail(String fromEmail) {
        staticFromEmail = fromEmail;
        log.info("发件人邮箱: {}", fromEmail);
    }

    @Value("${spring.mail.from.name:WisePen系统}")
    private void setFromName(String fromName) {
        staticFromName = fromName;
        log.info("发件人名称: {}", fromName);
    }

    /**
     * 发送重置密码邮件（静态方法，方便跨模块调用）
     */
    public static void sendResetMailStatic(String toEmail, String studentId, String resetLink) {
        // 检查依赖是否已初始化

        if (staticMailSender == null || staticTemplateEngine == null) {
            log.warn("邮件服务未初始化，无法发送邮件：收件人={}, 学号={}", toEmail, studentId);
            // 可以选择抛出异常或只是记录日志
            // throw new IllegalStateException("邮件服务未初始化：JavaMailSender 或 TemplateEngine 未注入");
            return;
        }

        try {
            // 创建邮件内容
            Context context = new Context();
            context.setVariable("student_id", studentId);
            context.setVariable("reset_link", resetLink);
            context.setVariable("current_date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));

            // 使用Thymeleaf模板引擎渲染邮件内容
            String content = staticTemplateEngine.process("resetMailTemplate", context);

            // 发送邮件
            sendMailStatic(toEmail, "WisePen密码重置通知", content);

            log.info("密码重置邮件发送成功：收件人={}, 学号={}", toEmail, studentId);
        } catch (Exception e) {
            log.error("发送密码重置邮件失败：收件人={}, 学号={}, 错误={}", toEmail, studentId, e.getMessage());
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    /**
     * 发送邮件的基础方法（静态方法）
     */
    private static void sendMailStatic(String to, String subject, String content) {
        // 检查依赖是否已初始化
        if (staticMailSender == null) {
            throw new IllegalStateException("邮件服务未初始化：JavaMailSender 未注入");
        }

        try {
            MimeMessage message = staticMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // 硬编码发件人地址以确保与用户名严格匹配
            String fromEmail = "25300130028@m.fudan.edu.cn";
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            log.info("当前使用的发件人账号: {}", staticFromEmail);
            staticMailSender.send(message);

            // 记录邮件发送日志（可选）
            saveMailLogStatic(to, subject, content, "resetMailTemplate");

        } catch (MessagingException e) {
            log.error("发送邮件失败：收件人={}, 主题={}, 错误={}", to, subject, e.getMessage());
            throw new RuntimeException("邮件发送失败", e);
        }
    }

    /**
     * 保存邮件发送记录（静态方法）
     */
    private static void saveMailLogStatic(String recipient, String subject, String content, String template) {
        SysMail mailLog = new SysMail();
        mailLog.setRecipient(recipient);
        mailLog.setSubject(subject);
        mailLog.setContent(content);
        mailLog.setTemplate(template);
        mailLog.setStatus(1); // 发送成功
        mailLog.setCreateTime(LocalDateTime.now());
        mailLog.setSendTime(LocalDateTime.now());

        // TODO: 如果需要保存到数据库，可以注入SysMailMapper并调用insert方法
        // sysMailMapper.insert(mailLog);
    }

    /**
     * 实例方法，用于Spring注入
     */
    public void sendResetMail(String toEmail, String studentId, String resetLink) {
        sendResetMailStatic(toEmail, studentId, resetLink);
    }
}