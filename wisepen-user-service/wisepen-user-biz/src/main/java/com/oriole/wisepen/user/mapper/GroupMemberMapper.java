package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 高频查询：获取某个用户加入的所有组ID
     */
    @Select("SELECT group_id FROM sys_group_member WHERE user_id = #{userId}")
    List<Long> selectGroupIdsByUserId(Long userId);
}