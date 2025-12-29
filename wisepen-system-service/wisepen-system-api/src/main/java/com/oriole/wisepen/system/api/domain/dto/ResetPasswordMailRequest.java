package com.oriole.wisepen.system.api.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 重置密码邮件DTO
 *
 * @author Oriole
 */
@Data
public class ResetPasswordMailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    //收件人邮箱
    @NotBlank(message = "收件人邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String toEmail;

    //学号/工号
    @NotBlank(message = "学号/工号不能为空")
    private String studentId;

     //重置密码链接
    @NotBlank(message = "重置密码链接不能为空")
    private String resetLink;
}