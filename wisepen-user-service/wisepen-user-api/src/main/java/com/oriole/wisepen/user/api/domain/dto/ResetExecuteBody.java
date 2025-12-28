package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.validation.CampusNo;
import com.oriole.wisepen.user.api.validation.Password;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.io.Serializable;

@Data
public class ResetExecuteBody implements Serializable {
    /** 学工号*/
    @NotBlank(message = "新密码不能为空")
    @Password
    private String newPassword;
    private String token;
}