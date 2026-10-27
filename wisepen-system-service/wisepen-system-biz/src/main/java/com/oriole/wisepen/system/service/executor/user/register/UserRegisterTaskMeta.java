package com.oriole.wisepen.system.service.executor.user.register;

import com.oriole.wisepen.user.api.domain.dto.req.UserRegisterReqDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UserRegisterTaskMeta {

    private List<UserRegisterReqDTO> users;
}
