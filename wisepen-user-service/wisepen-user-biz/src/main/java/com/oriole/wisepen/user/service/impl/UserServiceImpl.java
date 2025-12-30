package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.domain.entity.User;
import com.oriole.wisepen.user.domain.entity.UserProfile;
import com.oriole.wisepen.user.service.UserService;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    @Override
    public User getUserCoreInfoByUsername(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    @Override
    public UserInfoDTO getUserInfoById(Long userId) {
        // 查核心账号
        User user = userMapper.selectById(userId);

        if (user == null) {
            return null;
        }

        // 查档案详情
        UserProfile profile = userProfileMapper.selectById(user.getId());

        // 组装 DTO
        UserInfoDTO dto = new UserInfoDTO();
        BeanUtil.copyProperties(user, dto);

        if (profile != null) {
            BeanUtil.copyProperties(profile, dto);
        }

        return dto;
    }

    @Override
    public void updateUserStatus(Long userId, Status status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public User getUserCoreInfoByAccount(String account) {
        // 使用 MyBatis-Plus 的 Lambda 查询
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getDelFlag, 0)
                .and(wrapper -> wrapper
                        .eq(User::getUsername, account)
                        .or()
                        .eq(User::getCampusNo, account)
                )
                .last("LIMIT 1"));
    }

    @Override
    public boolean verifyExistCampusNo(String campusNo) {
        // 使用MyBatis-Plus的lambdaQuery查询
        return userProfileMapper.selectCount(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getCampusNo, campusNo)) > 0;
    }

    @Override
    public boolean verifyExistUsername(String username) {
        // 使用MyBatis-Plus的lambdaQuery查询
        return userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)) > 0;
    }

    @Override
    public boolean verifyExistUserId(String userId) {
        // 使用MyBatis-Plus的lambdaQuery查询，避免手写SQL
        return userProfileMapper.selectCount(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, Long.valueOf(userId))) > 0;
    }

    @Override
    public Long getUserIdByCampusNo(String campusNo) {
        // 用 Optional 处理查询结果，避免可能的空指针风险
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .select(User::getId)             // SQL: SELECT id
                        .eq(User::getCampusNo, campusNo) // SQL: WHERE campus_no = ?
                        .eq(User::getDelFlag, 0)         // SQL: AND del_flag = 0
                        .last("LIMIT 1")                 // SQL: LIMIT 1
        )).map(User::getId).orElse(null);
    }

    @Override
    public String getUserEmailByCampusNo(String campusNo) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getEmail) // 只查询 email 这一列
                .eq(User::getCampusNo, campusNo)
                .eq(User::getDelFlag, 0)
                .last("LIMIT 1"));

        return user != null ? user.getEmail() : null;
    }
    /**
     * 插入用户基本信息到sys_user表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertUser(User user) {
        return this.save(user);
    }

    /**
     * 插入用户档案信息到sys_user_profile表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertUserProfile(UserProfile userProfile) {
        return userProfileMapper.insert(userProfile) > 0;
    }

    /**
     * 根据用户ID更新密码
     * @param userId 用户ID
     * @param newPasswordHash 新密码的哈希值
     * @return 更新是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePasswordByUserId(String userId, String newPasswordHash) {
        try {
            User user = new User();
            user.setId(Long.valueOf(userId));
            user.setPassword(newPasswordHash);
            // 更新密码的同时更新时间戳
            user.setUpdateTime(java.time.LocalDateTime.now());

            int result = userMapper.updateById(user);
            return result > 0;
        } catch (NumberFormatException e) {
            log.error("用户ID格式错误: {}", userId, e);
            return false;
        }
    }

}