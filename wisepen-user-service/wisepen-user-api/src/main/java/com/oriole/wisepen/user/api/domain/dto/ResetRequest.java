package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.validation.ValidCampusNo;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class ResetBody implements Serializable {
    /** 学工号*/
    @NotBlank(message = "学工号不能为空")
    @ValidCampusNo
    private String campusNum;

    @NotBlank(message = "邮箱后缀不能为空")
    private String mailAppendix;
    /** 验证码 (预留) */
    private String code;
    /** 唯一标识 (预留，用于验证码校验) */
    private String uuid;
}