package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

/**
 * 重置密码执行请求体
 *
 * @author Oriole
 */
@Data
public class ResetExecuteVO implements Serializable {
    /** 新密码*/
    @NotBlank(message = "新密码不能为空")
    @ValidPassword
    private String newPassword;
    private String token;
}