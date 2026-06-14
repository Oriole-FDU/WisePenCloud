package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.domain.base.ResourceUserInteractionRecordBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 用户维度互动记录，存储每个用户对每个资源的点赞状态与评分
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_resource_user_interact_record")
@CompoundIndex(def = "{'resourceId': 1, 'userId': 1}", unique = true)
public class ResourceUserInteractionRecordEntity extends ResourceUserInteractionRecordBase {
    @Id
    private String id;

    private String resourceId;

    private String userId;

    @CreatedDate
    private LocalDateTime createTime;
    @LastModifiedDate
    private LocalDateTime updateTime;

    public ResourceUserInteractionRecordEntity(String resourceId, String userId) {
        super();
        this.resourceId = resourceId;
        this.userId = userId;
    }
}
