package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
    @Select("SELECT p.user_id FROM sys_user_profile p WHERE (p.campus_no = #{campus_no}) LIMIT 1")
    Long getUserIdByCampusNum(@Param("campus_no") String campus_no);
}