package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;

import java.util.Map;
import java.util.Set;

public interface IDisplayService {

    // 根据 userId 列表获取用户展示信息
    Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds);

    // 根据 userId 列表获取用户展示信息(额外字段)
    Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds, boolean includePrivateFields);

    // 根据 groupIds 列表获取小组展示信息
    Map<Long, GroupDisplayBase> getGroupDisplayInfoByIds(Set<Long> groupIds);
}
