package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class ResetRequest implements Serializable {
    /** 学工号*/
    @NotBlank(message = UserValidationMsg.CAMPUS_NO_EMPTY)
    private String campusNo;

    /** 验证码 (预留) */
    private String code;
    /** 唯一标识 (预留，用于验证码校验) */
    private String uuid;
}