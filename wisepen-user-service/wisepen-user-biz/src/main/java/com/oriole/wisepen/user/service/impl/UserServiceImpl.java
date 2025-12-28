package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.domain.entity.User;
import com.oriole.wisepen.user.domain.entity.UserProfile;
import com.oriole.wisepen.user.service.UserService;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void updateUserStatus(Long userId, Integer status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override//联合查询
    public User getUserCoreInfoByAccount(String account) {
        return userMapper.selectUserByAccount(account);
    }

    @Override
    public boolean verifyExistCampusNum(String campusNum) {
        return userMapper.verifyExistCampusNum(campusNum)>0;
    }

    @Override
    public boolean verifyExistUsername(String username) {
        return userMapper.verifyExistUsername(username)>0;
    }

    @Override
    public boolean verifyExistUserId(String userId) {
        return userMapper.verifyExistUserId(userId)>0;
    }

    @Override
    public Long getUserIdByCampusNum(String campusNum){
        return userProfileMapper.getUserIdByCampusNum(campusNum);
    }

    @Override
    public String getUserEmailByCampusNum(String campusNum){
        return userMapper.getUserEmailByCampusNum(campusNum);
    }
    /**
     * 插入用户基本信息到sys_user表
     * @param user 用户实体对象
     * @return 是否插入成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean insertUser(User user) {
        return this.save(user);
    }

    /**
     * 插入用户档案信息到sys_user_profile表
     * @param userProfile 用户档案实体对象
     * @return 是否插入成功
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