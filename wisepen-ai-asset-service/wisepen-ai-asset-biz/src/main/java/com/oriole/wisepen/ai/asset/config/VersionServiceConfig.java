package com.oriole.wisepen.ai.asset.config;

import com.oriole.wisepen.ai.asset.domain.entity.AgentVersionBundleEntity;
import com.oriole.wisepen.ai.asset.domain.entity.SkillVersionBundleEntity;
import com.oriole.wisepen.ai.asset.exception.AgentError;
import com.oriole.wisepen.ai.asset.exception.SkillError;
import com.oriole.wisepen.ai.asset.mq.AIAssetEventPublisher;
import com.oriole.wisepen.ai.asset.repository.AgentRepository;
import com.oriole.wisepen.ai.asset.repository.AgentVersionBundleRepository;
import com.oriole.wisepen.ai.asset.repository.SkillRepository;
import com.oriole.wisepen.ai.asset.repository.SkillVersionBundleRepository;
import com.oriole.wisepen.ai.asset.service.impl.VersionServiceImpl;
import com.oriole.wisepen.ai.asset.service.impl.VersionServiceProfile;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把同一份 VersionServiceImpl 按 skill / agent 差异装配成两个 Bean，按泛型类型自动注入到各调用方
 */
@Configuration
public class VersionServiceConfig {

    @Bean
    public VersionServiceImpl<SkillVersionBundleEntity> skillVersionService(
            SkillRepository skillRepository,
            SkillVersionBundleRepository skillVersionBundleRepository,
            RemoteStorageService remoteStorageService,
            AIAssetEventPublisher eventPublisher) {
        VersionServiceProfile<SkillVersionBundleEntity> profile = VersionServiceProfile.<SkillVersionBundleEntity>builder()
                .scene(StorageSceneEnum.PRIVATE_SKILL_ASSET)
                .logTag("skill")
                .repository(skillVersionBundleRepository)
                .draftFactory(SkillVersionBundleEntity::new)
                .publishedVersionLoader(resourceId -> skillRepository.findByResourceId(resourceId)
                        .orElseThrow(() -> new ServiceException(SkillError.SKILL_NOT_FOUND)).getVersion())
                .publishedVersionUpdater(skillRepository::updateVersionByResourceId)
                .versionNotFound(SkillError.SKILL_VERSION_NOT_FOUND)
                .nonDraft(SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION)
                .pathInvalid(SkillError.SKILL_ASSET_PATH_INVALID)
                .uploadApplyFailed(SkillError.SKILL_ASSET_UPLOAD_URL_APPLY_FAILED)
                .assetNotReady(SkillError.SKILL_ASSET_NOT_READY)
                .build();
        return new VersionServiceImpl<>(profile, remoteStorageService, eventPublisher);
    }

    @Bean
    public VersionServiceImpl<AgentVersionBundleEntity> agentVersionService(
            AgentRepository agentRepository,
            AgentVersionBundleRepository agentVersionBundleRepository,
            RemoteStorageService remoteStorageService,
            AIAssetEventPublisher eventPublisher) {
        VersionServiceProfile<AgentVersionBundleEntity> profile = VersionServiceProfile.<AgentVersionBundleEntity>builder()
                .scene(StorageSceneEnum.PRIVATE_AGENT_ASSET)
                .logTag("agent")
                .repository(agentVersionBundleRepository)
                .draftFactory(AgentVersionBundleEntity::new)
                .publishedVersionLoader(resourceId -> agentRepository.findByResourceId(resourceId)
                        .orElseThrow(() -> new ServiceException(AgentError.AGENT_NOT_FOUND)).getVersion())
                .publishedVersionUpdater(agentRepository::updateVersionByResourceId)
                .versionNotFound(AgentError.AGENT_VERSION_NOT_FOUND)
                .nonDraft(AgentError.CANNOT_OPERATE_NON_DRAFT_AGENT_VERSION)
                .pathInvalid(AgentError.AGENT_ASSET_PATH_INVALID)
                .uploadApplyFailed(AgentError.AGENT_ASSET_UPLOAD_URL_APPLY_FAILED)
                .assetNotReady(AgentError.AGENT_ASSET_NOT_READY)
                .build();
        return new VersionServiceImpl<>(profile, remoteStorageService, eventPublisher);
    }
}
