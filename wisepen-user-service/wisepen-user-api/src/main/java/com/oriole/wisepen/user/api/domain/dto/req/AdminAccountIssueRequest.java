package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class AdminAccountIssueRequest implements Serializable {

    @NotBlank
    @ValidUsername
    private String username;
}
