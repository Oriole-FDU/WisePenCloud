package com.oriole.wisepen.ai.asset.service;

import com.oriole.wisepen.ai.asset.domain.entity.AIResourceBaseEntity;
import com.oriole.wisepen.ai.asset.domain.entity.VersionBundleBaseEntity;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadCancelRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;

import java.util.List;

public interface IVersionService<VT extends VersionBundleBaseEntity<VT>> {

    void createDraftVersion(String resourceId, Integer draftVersion);

    void forkPublishedVersionSnapshot(String sourceResourceId, Integer sourceVersion, String targetResourceId);

    VT getVersionBundle(String resourceId, Integer version);

    AssetUploadInitResponse initUploadAssets(AssetUploadInitRequest req);

    void cancelUploadAssets(AssetUploadCancelRequest req);

    void deleteAssets(AssetDeleteRequest req);

    void publishVersion(String resourceId);

    void handleFileUploaded(FileUploadedMessage message);

    void deleteAllVersionsByResourceIds(List<String> resourceIds);
}
