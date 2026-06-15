package com.oriole.wisepen.ai.asset.service.impl;

import com.oriole.wisepen.ai.asset.domain.dto.req.AgentSpecUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.entity.AgentEntity;
import com.oriole.wisepen.ai.asset.domain.entity.AgentVersionBundleEntity;
import com.oriole.wisepen.ai.asset.enums.VersionStatus;
import com.oriole.wisepen.ai.asset.exception.AIResourceError;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.repository.AgentRepository;
import com.oriole.wisepen.ai.asset.repository.AgentVersionBundleRepository;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * agent 版本生命周期，绑定 agent 的版本包仓库与主档仓库，并维护草稿运行配置
 */
@Service
public class AgentVersionServiceImpl extends VersionServiceImpl<AgentVersionBundleEntity, AgentEntity> {

    public AgentVersionServiceImpl(AgentVersionBundleRepository versionRepository,
                                   AgentRepository agentRepository,
                                   RemoteStorageService remoteStorageService,
                                   AIAssetEventPublisher eventPublisher) {
        super(remoteStorageService, eventPublisher);
        this.versionBundleBaseRepository = versionRepository;
        this.aiResourceBaseRepository = agentRepository;
    }

    @Override
    protected AgentVersionBundleEntity buildDraft(String resourceId, Integer draftVersion) {
        return AgentVersionBundleEntity.builder()
                .resourceId(resourceId)
                .version(draftVersion)
                .status(VersionStatus.DRAFT)
                .assets(new ArrayList<>())
                .build();
    }

    @Override
    protected StorageSceneEnum getStorageScene() {
        return StorageSceneEnum.PRIVATE_AGENT_ASSET;
    }

    // 整体替换草稿版本的运行配置，agent 专属
    public void updateDraftSpec(AgentSpecUpdateRequest req) {
        AgentVersionBundleEntity draft = versionBundleBaseRepository.findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_VERSION);
        draft.setSpec(req.getSpec());
        versionBundleBaseRepository.save(draft);
    }
}
