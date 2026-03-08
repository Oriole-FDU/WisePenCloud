package com.oriole.wisepen.file.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息实体类
 *
 * @author Ian.xiong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_file")
public class FileInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long fileId;

    private String filename;

    private String md5;

    private String url;

    private String type;

    private Long size;

    @TableField("pdf_url")
    private String pdfUrl;

    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 上传者ID
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 状态 0:处理中 1:成功 2:失败
     */
    private Integer status;

    /**
     * 对应 resource-service 的资源 ID（注册完成后写入）
     */
    @TableField("resource_id")
    private String resourceId;
}
