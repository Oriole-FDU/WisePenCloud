package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.validation.CampusNo;
import com.oriole.wisepen.user.api.validation.Password;
import com.oriole.wisepen.user.api.validation.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class RegisterBody implements Serializable {
    /** 用户名*/
    @NotBlank(message = "用户名不能为空")
    @Username(
        treatSpecialFormatAsExisting = true,
        specialFormatMessage = "用户名已存在"
    )
    private String username;

    /** 密码*/
    @NotBlank(message = "密码不能为空")
    @Password
    private String password;

    /** 真名*/
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名长度不能超过64位")
    private String realName;

    /** 学工号*/
    @NotBlank(message = "学工号不能为空")
    @CampusNo
    private String campusNum;
}
