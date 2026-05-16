package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import com.oriole.wisepen.resource.enums.AclGrantMode;
import com.oriole.wisepen.resource.enums.ResourceMountMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_tags")
public class TagEntity extends TagInfoBase {
    @Id
    private String tagId;
    private String parentId;     // 父节点 ID，根节点可设为 "0" 或 null

    // 祖先数组，例如：["root_id", "level1_id", "level2_id"], 用于子树查询和级联删除
    private List<String> ancestors;

    // 权限配置
    private AclGrantMode aclGrantMode;
    private ResourceMountMode resourceMountMode;
    private List<String> aclGrantSpecifiedUsers; // 配合白名单/黑名单使用的 userId 列表
    private List<String> resourceMountSpecifiedUsers; // 资源挂载白名单/黑名单用户列表
    private Integer grantedActionsMask;  // 匹配该标签时授予的权限掩码

    @CreatedDate
    private LocalDateTime createTime;
    @LastModifiedDate
    private LocalDateTime updateTime;
}