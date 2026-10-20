package com.oriole.wisepen.user.mapper.model;

import lombok.Data;

/**
 * 小组成员数统计结果。
 */
@Data
public class GroupMemberCount {
    private Long groupId;
    private Long memberCount;
}
