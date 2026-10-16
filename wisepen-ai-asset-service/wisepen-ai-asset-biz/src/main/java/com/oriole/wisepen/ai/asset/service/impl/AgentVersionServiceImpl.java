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
import java.time.LocalDateTime;


@Service
public class AgentVersionServiceImpl extends VersionServiceImpl<AgentVersionBundleEntity, AgentEntity> {

    private final AgentVersionBundleRepository agentVersionBundleRepository;

    public AgentVersionServiceImpl(AgentVersionBundleRepository agentVersionBundleRepository,
                                   AgentRepository agentRepository,
                                   RemoteStorageService remoteStorageService,
                                   AIAssetEventPublisher eventPublisher) {
        super(agentVersionBundleRepository, agentRepository, remoteStorageService, eventPublisher);
        this.agentVersionBundleRepository = agentVersionBundleRepository;
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

    public void updateAgentSpec(AgentSpecUpdateRequest req) {
        AgentVersionBundleEntity draft = this.versionBundleBaseRepository.findByResourceIdAndVersion(req.getResourceId(), req.getDraftVersion())
                .orElseThrow(() -> new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND));
        if (draft.getStatus() != VersionStatus.DRAFT) throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_AI_RESOURCE_VERSION);
        // 只更新配置，避免整份文档保存覆盖并发上传结果。
        if (agentVersionBundleRepository.updateDraftSpec(draft.getId(), req.getSpec(), LocalDateTime.now()) == 0) {
            throw new ServiceException(AIResourceError.CANNOT_OPERATE_NON_DRAFT_AI_RESOURCE_VERSION);
        }
    }
}
