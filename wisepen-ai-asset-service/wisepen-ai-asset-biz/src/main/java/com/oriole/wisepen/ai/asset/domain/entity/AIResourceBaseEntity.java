package com.oriole.wisepen.ai.asset.domain.entity;

import com.oriole.wisepen.ai.asset.domain.base.AIResourceInfoBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * skill / agent 资源主档实体的公共字段
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public abstract class AIResourceBaseEntity extends AIResourceInfoBase {

    @Id
    private String resourceId;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
