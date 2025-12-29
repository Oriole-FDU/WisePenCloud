package com.oriole.wisepen.user.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.feign.dto.MailResultDTO;
import com.oriole.wisepen.user.api.feign.dto.ResetPasswordMailDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 邮件发送远程调用降级处理
 *
 * @author Oriole
 */
@Component
@Slf4j
public class RemoteMailServiceFallbackFactory implements FallbackFactory<RemoteMailService> {

    @Override
    public RemoteMailService create(Throwable cause) {
        return new RemoteMailService() {
            @Override
            public R<MailResultDTO> sendResetPasswordMail(ResetPasswordMailDTO resetMailDTO) {
                log.error("发送重置密码邮件失败，触发降级：收件人={}, 学号={}, 错误={}",
                        resetMailDTO.getToEmail(), resetMailDTO.getStudentId(), cause.getMessage());

                // 返回降级响应
                MailResultDTO result = new MailResultDTO();
                result.setSuccess(false);
                result.setErrorMessage("邮件服务暂不可用，请稍后重试");
                return R.ok(result);
            }
        };
    }
}