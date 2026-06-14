package com.oriole.wisepen.ai.asset.service;

import com.oriole.wisepen.ai.asset.domain.entity.BaseVersionBundleEntity;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;

import java.util.List;

/**
 * skill / agent 版本生命周期的统一接口，由 VersionServiceImpl 经 VersionServiceConfig 装配两个 Bean 实现
 */
public interface IVersionService<T extends BaseVersionBundleEntity> {

    void createDraft(String resourceId, Integer draftVersion);

    // 返回 entity（组成信息），DTO 由 controller 层组装
    T getBundle(String resourceId, Integer version);

    AssetUploadInitResponse initUploadAssets(AssetUploadInitRequest req);

    void deleteAssets(AssetDeleteRequest req);

    void publishVersion(String resourceId);

    void handleFileUploaded(FileUploadedMessage message);

    void deleteAllVersionsByResourceIds(List<String> resourceIds);
}
