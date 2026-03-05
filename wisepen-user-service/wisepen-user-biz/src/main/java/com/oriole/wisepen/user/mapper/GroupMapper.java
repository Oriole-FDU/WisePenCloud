package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.Group;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GroupMapper extends BaseMapper<Group> {
}