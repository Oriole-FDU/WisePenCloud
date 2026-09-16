package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.domain.entity.GroupEntity;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.service.IDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DisplayServiceImpl implements IDisplayService {

    private final UserMapper userMapper;
    private final GroupMapper groupMapper;

    @Override
    public Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds) {
        return getUserDisplayInfoByIds(userIds, false);
    }

    @Override
    public Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds, boolean includePrivateFields) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        List<UserEntity> userList = userMapper.selectBatchIds(userIds);

        if (CollectionUtils.isEmpty(userList)) {
            return Collections.emptyMap();
        }

        return userList.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                UserEntity::getUserId,
                user -> {
                    UserDisplayBase response = BeanUtil.copyProperties(user, UserDisplayBase.class);
                    if (!includePrivateFields) {
                        response.setRealName(null);
                        response.setCampusNo(null);
                        response.setEmail(null);
                        response.setMobile(null);
                    }
                    return response;
                },
                (existing, replacement) -> existing));
    }

    @Override
    public Map<Long, GroupDisplayBase> getGroupDisplayInfoByIds(Set<Long> groupIds) {
        if (CollectionUtils.isEmpty(groupIds)) {
            return Collections.emptyMap();
        }
        List<GroupEntity> groupList = groupMapper.selectBatchIds(groupIds);

        if (CollectionUtils.isEmpty(groupList)) {
            return Collections.emptyMap();
        }

        return groupList.stream().filter(Objects::nonNull).collect(Collectors.toMap(
                GroupEntity::getGroupId,
                group -> BeanUtil.copyProperties(group, GroupDisplayBase.class),
                (existing, replacement) -> existing
        ));
    }
}
