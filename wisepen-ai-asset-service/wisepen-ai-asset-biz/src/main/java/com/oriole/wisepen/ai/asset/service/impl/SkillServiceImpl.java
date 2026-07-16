package com.oriole.wisepen.ai.asset.service.impl;

import com.oriole.wisepen.ai.asset.domain.entity.SkillEntity;
import com.oriole.wisepen.ai.asset.domain.entity.SkillVersionBundleEntity;
import com.oriole.wisepen.ai.asset.enums.AIResourceSourceType;
import com.oriole.wisepen.ai.asset.repository.AIResourceBaseRepository;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SkillServiceImpl extends AIResourceServiceImpl<SkillEntity, SkillVersionBundleEntity>{

    public SkillServiceImpl(AIResourceBaseRepository<SkillEntity> aiResourceBaseRepository, IVersionService<SkillVersionBundleEntity> skillVersionService, RemoteResourceService remoteResourceService) {
        super(aiResourceBaseRepository, skillVersionService, remoteResourceService);
    }

    @Override
    protected SkillEntity buildNewResource(String resourceId, String name, String description, AIResourceSourceType skillSourceType) {
        return SkillEntity.builder()
                .resourceId(resourceId).name(name).description(description).version(0).sourceType(skillSourceType)
                .build();
    }

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.SKILL;
    }
}
