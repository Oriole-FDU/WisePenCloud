package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户信息
     * @param username 用户名
     * @return 用户实体类
     */
    @Select("SELECT u.* " +
            "FROM sys_user u " +
            "WHERE u.username = #{username} " +
            "AND u.del_flag = 0 " +
            "LIMIT 1")
    User selectUserByUsername(@Param("username") String username);

    /**
     * 根据学工号查询用户信息（通过用户档案表）
     * @param campusNo 学工号
     * @return 用户实体类（包含campusNo字段）
     */
    @Select("SELECT u.*, p.campus_no " +
            "FROM sys_user u " +
            "INNER JOIN sys_user_profile p ON u.id = p.user_id " +
            "WHERE p.campus_no = #{campusNo} " +
            "AND u.del_flag = 0 " +
            "LIMIT 1")
    User selectUserByCampusNo(@Param("campusNo") String campusNo);

    @Select("SELECT u.email FROM sys_user u " +
            "LEFT JOIN sys_user_profile p ON u.id = p.user_id " +
            "WHERE p.campus_no = #{campus_no} LIMIT 1")
    String getUserEmailByCampusNum(@Param("campus_no") String campus_no);
}