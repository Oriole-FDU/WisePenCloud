package com.oriole.wisepen.ai.asset.service;

import com.oriole.wisepen.ai.asset.domain.entity.AIResourceBaseEntity;
import com.oriole.wisepen.ai.asset.domain.entity.VersionBundleBaseEntity;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;

import java.util.List;

/**
 * skill / agent 版本生命周期的统一接口，由抽象的 VersionServiceImpl 模板实现，子类绑定具体类型
 */
public interface IVersionService<VT extends VersionBundleBaseEntity, AT extends AIResourceBaseEntity> {

    void createDraft(String resourceId, Integer draftVersion);

    // 返回 entity（组成信息），DTO 由 controller 层组装
    VT getBundle(String resourceId, Integer version);

    AssetUploadInitResponse initUploadAssets(AssetUploadInitRequest req);

    void deleteAssets(AssetDeleteRequest req);

    void publishVersion(String resourceId);

    void handleFileUploaded(FileUploadedMessage message);

    void deleteAllVersionsByResourceIds(List<String> resourceIds);
}
