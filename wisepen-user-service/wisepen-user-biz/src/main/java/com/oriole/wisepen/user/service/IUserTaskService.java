package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskStatusResponse;

import java.util.List;

public interface IUserTaskService {

    Object complete(Long userId, UserTaskCode taskCode, Long operatorId, String meta);

    List<UserTaskStatusResponse> listTaskStatus(Long userId);
}
