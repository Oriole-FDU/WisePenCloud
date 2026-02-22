package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.GroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.base.ResourceItemInfoBase;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "wisepen_resource_items")
public class ResourceItemEntity extends ResourceItemInfoBase {
    @Id
    private String resourceId; // 资源全局唯一ID

    private List<GroupTagBind> groupBinds;
    private List<GroupAcl> computedAcls;

    private Date createTime;
    private Date updateTime;
}