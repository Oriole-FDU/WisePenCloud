package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMemberEntity> {

    void updateAiBillingPriorities(
            @Param("userId") Long userId,
            @Param("orderedGroupIds") List<Long> orderedGroupIds
    );
}
