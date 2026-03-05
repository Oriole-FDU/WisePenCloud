package com.oriole.wisepen.user.service.impl;

import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createGroup(Group group) {
        // 可以在这里加业务逻辑，比如：校验组名重复
        // ...

        // 调用 MP 的 Mapper 方法
        groupMapper.insert(group);
    }

    @Override
    public List<Long> getGroupIdsByUserId(Long userId) {
        return groupMemberMapper.selectGroupIdsByUserId(userId);
    }

    @Override
    public Group getGroupById(Long id) {
        return groupMapper.selectById(id);
    }
}