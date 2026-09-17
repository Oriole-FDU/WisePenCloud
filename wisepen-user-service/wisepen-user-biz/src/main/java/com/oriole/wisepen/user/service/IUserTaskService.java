package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.enums.UserTaskCode;

public interface IUserTaskService {

    Object complete(Long userId, UserTaskCode taskCode, Long operatorId, String meta);
}
