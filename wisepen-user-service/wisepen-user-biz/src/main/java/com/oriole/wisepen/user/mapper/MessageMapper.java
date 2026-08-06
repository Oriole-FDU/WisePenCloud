package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.MessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    @Update("""
            UPDATE sys_message
            SET read_count = read_count + #{delta}
            WHERE message_id = #{messageId}
            """)
    int incrementReadCount(@Param("messageId") Long messageId, @Param("delta") Long delta);
}
