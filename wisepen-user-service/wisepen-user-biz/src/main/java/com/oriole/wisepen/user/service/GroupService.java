package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.domain.entity.Group;

import java.util.List;
import java.util.Map;

public interface GroupService {
    // 创建分组
    void createGroup(Group group);

    // 获取用户的所有组ID和角色
    Map<String, Integer> getGroupRoleMapByUserId(Long userId);

    // 获取用户的所有组ID
    List<Long> getGroupIdsByUserId(Long userId);

    // 获取组的详情
    Group getGroupById(Long id);
}
