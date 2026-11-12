package com.oriole.wisepen.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.system.api.domain.base.SysOperLogBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("operation_log")
public class SysOperLogEntity extends SysOperLogBase {
    @TableId(type = IdType.AUTO)
    private Long id;
}