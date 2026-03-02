package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.base.TagInfoBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "wisepen_tags")
public class TagEntity extends TagInfoBase {
    @Id
    private String tagId;
    private String parentId;     // 父节点 ID，根节点可设为 "0" 或 null

    // 祖先数组，例如：["root_id", "level1_id", "level2_id"], 用于子树查询和级联删除
    private List<String> ancestors;

    private Date createTime;
    private Date updateTime;
}