package com.oriole.wisepen.user.service.impl;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.service.MailDrawingService;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import cn.hutool.core.date.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDrawingServiceImpl implements MailDrawingService {
    private final TemplateEngine templateEngine;

    // 定义模板名称常量，防止硬编码多次出现
    private static final String RESET_MAIL_TEMPLATE = "resetMailTemplate";

    @Override
    public String resetMailDrawing(String campusNo, String resetLink) {
        try {
            Context context = new Context();

            // 变量名建议与之前 MailSendDTO 的 key 保持一致
            context.setVariable("student_id", campusNo);
            context.setVariable("reset_link", resetLink);
            context.setVariable("current_date", DateUtil.now());

            // Thymeleaf 渲染
            return templateEngine.process(RESET_MAIL_TEMPLATE, context);

        } catch (Exception e) {
            log.error("渲染重置密码邮件模板失败：学号={}, 错误={}", campusNo, e.getMessage());
            // 建议传入原始异常 e，方便排查是模板语法错误还是空指针
            throw new ServiceException("");
        }
    }
}
