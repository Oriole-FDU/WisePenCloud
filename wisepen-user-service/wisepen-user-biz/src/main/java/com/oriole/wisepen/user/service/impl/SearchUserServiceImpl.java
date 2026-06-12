package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchResponse;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.service.ISearchUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchUserServiceImpl implements ISearchUserService {

    private final UserMapper userMapper;
    private final GroupMemberMapper groupMemberMapper;

    @Override
    public PageR<UserSearchResponse> searchUsers(String keyword, List<Long> groupIds, int page, int size) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new PageR<>(0, page, size);
        }

        LambdaQueryWrapper<UserEntity> queryWrapper = Wrappers.<UserEntity>lambdaQuery()
                .select(UserEntity::getUserId, UserEntity::getNickname, UserEntity::getRealName,
                        UserEntity::getAvatar, UserEntity::getIdentityType)
                .eq(UserEntity::getStatus, Status.NORMAL)
                .orderByDesc(UserEntity::getUpdateTime);

        if (StrUtil.isNotBlank(keyword)) {
            String kw = keyword.trim();
            queryWrapper.and(wrapper -> wrapper.like(UserEntity::getNickname, kw)
                    .or().like(UserEntity::getRealName, kw)
                    .or().eq(UserEntity::getUsername, kw)
                    .or().eq(UserEntity::getCampusNo, kw));
        }

        Set<Long> groupMemberUserIds = groupMemberMapper.selectList(Wrappers.<GroupMemberEntity>lambdaQuery()
                        .select(GroupMemberEntity::getUserId)
                        .in(GroupMemberEntity::getGroupId, groupIds))
                .stream()
                .map(GroupMemberEntity::getUserId)
                .collect(Collectors.toSet());
        if (groupMemberUserIds.isEmpty()) {
            return new PageR<>(0, page, size);
        }
        queryWrapper.in(UserEntity::getUserId, groupMemberUserIds);

        Page<UserEntity> result = userMapper.selectPage(new Page<>(page, size), queryWrapper);
        List<UserSearchResponse> records = result.getRecords().stream()
                .map(userEntity -> BeanUtil.copyProperties(userEntity, UserSearchResponse.class))
                .toList();

        PageR<UserSearchResponse> pageR = new PageR<>(result.getTotal(), page, size);
        pageR.addAll(records);
        return pageR;
    }
}
