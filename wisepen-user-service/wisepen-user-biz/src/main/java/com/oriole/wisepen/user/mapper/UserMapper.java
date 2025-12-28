package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名或学工号查询用户信息（联合查询）
     * @param account 用户名或学工号
     * @return 用户实体类（包含campusNo字段）
     */
    @Select("SELECT u.*, p.campus_no " +
            "FROM sys_user u " +
            "LEFT JOIN sys_user_profile p ON u.id = p.user_id " +
            "WHERE (u.username = #{account} OR p.campus_no = #{account}) " +
            "AND u.del_flag = 0 " +
            "LIMIT 1")
    User selectUserByAccount(@Param("account") String account);

    @Select("SELECT COUNT(*) FROM sys_user u WHERE (u.username = #{username}) LIMIT 1")
    Integer verifyExistUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM sys_user_profile WHERE campus_no = #{campus_no} LIMIT 1")
    Integer verifyExistCampusNum(@Param("campus_no") String campus_no);

    @Select("SELECT COUNT(*) FROM sys_user_profile WHERE user_id = #{UserId} LIMIT 1")
    Integer verifyExistUserId(@Param("user_id") String UserId);

    @Select("SELECT u.email FROM sys_user u " +
            "LEFT JOIN sys_user_profile p ON u.id = p.user_id " +
            "WHERE p.campus_no = #{campus_no} LIMIT 1")
    String getUserEmailByCampusNum(@Param("campus_no") String campus_no);
}