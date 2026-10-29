package com.oriole.wisepen.file.storage.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.oriole.wisepen.file.storage.api.domain.base.StorageRecordBase;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物理存储记录实体类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("storage_record")
public class StorageRecordEntity extends StorageRecordBase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long fileId;

    /**
     * 该文件所在的存储提供商编码
     */
    private String providerCode;

    private StorageStatusEnum status;

    private StorageSceneEnum scene;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}