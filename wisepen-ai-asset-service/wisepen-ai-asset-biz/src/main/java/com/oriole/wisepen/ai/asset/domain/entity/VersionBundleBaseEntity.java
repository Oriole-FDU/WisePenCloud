package com.oriole.wisepen.ai.asset.domain.entity;

import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.domain.base.VersionBundleBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public abstract class VersionBundleBaseEntity<T extends VersionBundleBaseEntity<T>> extends VersionBundleBase {

    @Id
    private String id;

    private String resourceId;

    // 上传成功前仅保存候选文件，assets 继续指向最后一次成功的内容。
    private List<AssetInfoBase> pendingAssets;

    // 条件更新使用的修订号；旧文档缺省为 null，首次更新时由 MongoDB 原子递增。
    private Long revision;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;

    // 发布前的类型相关校验：skill 校验核心文件，agent 校验 spec
    public abstract void checkCoreAssetReady();

    public abstract void copyBy(T entity);
}
