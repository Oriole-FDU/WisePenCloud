package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.domain.entity.Group;

import java.util.List;
public interface GroupService {
    // 创建分组 (业务方法)
    void createGroup(Group group);

    // 获取用户的所有组ID (业务方法)
    List<Long> getGroupIdsByUserId(Long userId);

    // 获取组的详情 (业务方法)
    Group getGroupById(Long id);
}
