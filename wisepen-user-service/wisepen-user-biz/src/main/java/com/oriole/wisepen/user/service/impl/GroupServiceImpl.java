package com.oriole.wisepen.user.service.impl;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public Map<String, Integer> getGroupRoleMapByUserId(Long userId) {
        List<GroupMember> members = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getUserId, userId)
                        .select(GroupMember::getGroupId, GroupMember::getRole)
        );
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyMap();
        }
        return members.stream()
                .collect(Collectors.toMap(
                        member -> String.valueOf(member.getGroupId()),
                        member -> member.getRole().getCode()
                ));
    }

    @Override
    public List<Long> getGroupIdsByUserId(Long userId) {
        return groupMemberMapper.selectGroupIdsByUserId(userId);
    }

    @Override
    public Group getGroupById(Long id) {
        return groupMapper.selectById(id);
    }

    @Override
    public void updateGroup(Group group) {
        groupMapper.updateById(group);
    }

    @Override
    public void deleteGroup(Long groupId) {
        groupMapper.deleteById(groupId);
    }

    @Override
    public com.oriole.wisepen.user.api.domain.dto.PageResp<com.oriole.wisepen.user.api.domain.dto.GroupQueryResp> getGroupIds(Long userId, Integer relationType, Integer page, Integer size) {
        // Simple dummy implementation or return empty page just to pass compilation, 
        // since the user wants it to run for file-service test, but let's do a basic query.
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<GroupMember> mpPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getUserId, userId);
        if (relationType != null && relationType == 1) {
            wrapper.eq(GroupMember::getRole, com.oriole.wisepen.common.core.domain.enums.GroupRoleType.OWNER);
        } else if (relationType != null && relationType == 2) {
            wrapper.ne(GroupMember::getRole, com.oriole.wisepen.common.core.domain.enums.GroupRoleType.OWNER);
        }
        com.baomidou.mybatisplus.core.metadata.IPage<GroupMember> memberRecords = groupMemberMapper.selectPage(mpPage, wrapper);
        
        List<com.oriole.wisepen.user.api.domain.dto.GroupQueryResp> resps = memberRecords.getRecords().stream().map(gm -> {
            Group g = groupMapper.selectById(gm.getGroupId());
            com.oriole.wisepen.user.api.domain.dto.GroupQueryResp resp = new com.oriole.wisepen.user.api.domain.dto.GroupQueryResp();
            if (g != null) {
                resp.setId(g.getId());
                resp.setName(g.getName());
                resp.setOwnerId(g.getOwnerId());
                resp.setType(g.getType());
                resp.setDescription(g.getDescription());
                resp.setCoverUrl(g.getCoverUrl());
                resp.setInviteCode(g.getInviteCode());
                resp.setMemberCount(groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, g.getId())).intValue());
            }
            return resp;
        }).collect(Collectors.toList());
        return new com.oriole.wisepen.user.api.domain.dto.PageResp<>((int) memberRecords.getPages(), resps);
    }
}