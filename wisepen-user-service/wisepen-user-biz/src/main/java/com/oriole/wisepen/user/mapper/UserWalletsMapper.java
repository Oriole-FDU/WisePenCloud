package com.oriole.wisepen.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.oriole.wisepen.user.domain.entity.UserWalletEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserWalletsMapper extends BaseMapper<UserWalletEntity> {

    @Select("SELECT user_id, token_balance, token_used, coin_balance, update_time " +
            "FROM sys_user_wallets WHERE user_id = #{userId} FOR UPDATE")
    UserWalletEntity selectByIdForUpdate(@Param("userId") Long userId);
}
