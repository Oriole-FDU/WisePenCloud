package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.ComputedGroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.base.ResourceInteractionInfoBase;
import com.oriole.wisepen.resource.domain.base.ResourceItemInfoBase;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "wisepen_resource_items")
public class ResourceItemEntity extends ResourceItemInfoBase {
    @Id
    private String resourceId; // 资源全局唯一ID

    private List<GroupTagBind> groupBinds = new ArrayList<>();

    // 预计算后的运行时权限
    private Map<String, ComputedGroupAcl> computedGroupAcls;

    /** 资源级强覆盖：若非空，将无视 computedGroupAcls 的值 */
    private Integer overrideGrantedActionsMask;

    /** 资源级绝对用户特权：若用户命中此 Map，直接返回该值，无视其他所有规则 */
    private Map<String, Integer> specifiedUsersGrantedActionsMask;

    @CreatedDate
    private LocalDateTime createTime;
    @LastModifiedDate
    private LocalDateTime updateTime;

    /** 资源删除时间，非 null 表示已删除；定时任务据此判断是否到期硬删*/
    private LocalDateTime deletedAt;
}