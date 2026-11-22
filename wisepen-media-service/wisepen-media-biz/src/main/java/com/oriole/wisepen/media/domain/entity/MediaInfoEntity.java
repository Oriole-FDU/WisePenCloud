package com.oriole.wisepen.media.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** 媒体资源级信息，按 resourceId 键；finalize 注册资源时创建。 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wisepen_media_info")
public class MediaInfoEntity implements Persistable<String> {

    @Id
    private String resourceId;

    private Long ownerId;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    @Override
    public String getId() {
        return resourceId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return createTime == null;
    }
}
