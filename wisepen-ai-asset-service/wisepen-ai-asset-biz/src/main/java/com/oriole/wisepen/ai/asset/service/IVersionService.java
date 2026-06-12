package com.oriole.wisepen.ai.asset.service;

import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillVersionPublishRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillVersionBundleInfoResponse;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;

import java.util.List;

public interface IVersionService {

    void createDraftSkillVersion(String resourceId, Integer draftVersion);

    SkillVersionBundleInfoResponse getSkillVersionBundle(String resourceId, Integer version);

    AssetUploadInitResponse initUploadSkillAssets(AssetUploadInitRequest req);

    void deleteSkillAssets(AssetDeleteRequest req);

    void publishSkillVersion(SkillVersionPublishRequest req);

    void handleFileUploaded(FileUploadedMessage message);

    void deleteAllVersionsByResourceIds(List<String> resourceIds);
}
