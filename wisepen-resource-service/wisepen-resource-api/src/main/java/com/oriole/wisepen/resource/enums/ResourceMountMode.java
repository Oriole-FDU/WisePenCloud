package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资源挂载权限模式枚举
 */
@Getter
@AllArgsConstructor
public enum ResourceMountMode {

    ALL(0),        // 全员可挂载
    ONLY_ADMIN(1), // 仅管理员可挂载
    WHITELIST(2),  // 仅白名单内用户可挂载
    BLACKLIST(3);  // 仅黑名单外用户可挂载

    @EnumValue
    @JsonValue
    private final int code;
}
