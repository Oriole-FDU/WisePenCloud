package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.user.api.domain.base.GroupMemberBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("group_member")
public class GroupMemberEntity extends GroupMemberBase {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long groupId; // 组ID
    private Long userId; // 用户ID

    // 当前用户使用多个小组支付 AI 费用时的偏好顺序，null 表示未主动排序
    private Integer aiBillingPriority;
}