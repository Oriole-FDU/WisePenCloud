package com.oriole.wisepen.ai.asset.service.impl;

import com.oriole.wisepen.ai.asset.domain.entity.SkillEntity;
import com.oriole.wisepen.ai.asset.domain.entity.SkillVersionBundleEntity;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.repository.SkillRepository;
import com.oriole.wisepen.ai.asset.repository.SkillVersionBundleRepository;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * skill 版本生命周期，绑定 skill 的版本包仓库与主档仓库
 */
@Service
public class SkillVersionServiceImpl extends VersionServiceImpl<SkillVersionBundleEntity, SkillEntity> {

    public SkillVersionServiceImpl(SkillVersionBundleRepository versionRepository,
                                   SkillRepository skillRepository,
                                   RemoteStorageService remoteStorageService,
                                   AIAssetEventPublisher eventPublisher) {
        super(remoteStorageService, eventPublisher);
        this.versionBundleBaseRepository = versionRepository;
        this.aiResourceBaseRepository = skillRepository;
    }

    @Override
    protected SkillVersionBundleEntity buildDraft(String resourceId, Integer draftVersion) {
        return SkillVersionBundleEntity.builder()
                .resourceId(resourceId)
                .version(draftVersion)
                .status(VersionStatus.DRAFT)
                .assets(new ArrayList<>())
                .build();
    }

    @Override
    protected StorageSceneEnum getStorageScene() {
        return StorageSceneEnum.PRIVATE_SKILL_ASSET;
    }
}
