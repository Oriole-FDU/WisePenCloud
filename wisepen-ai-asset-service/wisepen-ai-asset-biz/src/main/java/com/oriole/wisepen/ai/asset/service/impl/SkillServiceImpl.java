package com.oriole.wisepen.ai.asset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.domain.base.SkillInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillCreateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillMetaInfoResponse;
import com.oriole.wisepen.ai.asset.domain.entity.SkillEntity;
import com.oriole.wisepen.ai.asset.enums.SkillSourceType;
import com.oriole.wisepen.ai.asset.exception.SkillError;
import com.oriole.wisepen.ai.asset.repository.SkillRepository;
import com.oriole.wisepen.ai.asset.service.ISkillService;
import com.oriole.wisepen.ai.asset.service.IVersionService;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements ISkillService {

    private final SkillRepository skillRepository;
    private final IVersionService skillVersionService;
    private final RemoteResourceService remoteResourceService;

    @Override
    public String createSkill(SkillCreateRequest req, String userId) {
        String resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                .resourceName(req.getTitle())
                .resourceType(ResourceType.SKILL)
                .ownerId(userId)
                .build()).getData();
        if (!StringUtils.hasText(resourceId)) {
            throw new ServiceException(SkillError.SKILL_RESOURCE_REGISTER_FAILED);
        }

        SkillEntity entity = SkillEntity.builder()
                .resourceId(resourceId)
                .name(req.getName() == null ? "" : req.getName())
                .description(req.getDescription() == null ? "" : req.getDescription())
                .version(0)
                .sourceType(req.getSourceType() == null ? SkillSourceType.MANUAL : req.getSourceType())
                .build();
        skillRepository.save(entity);
        // 直接新建首份草案(1)
        skillVersionService.createDraftSkillVersion(resourceId, 1);
        return resourceId;
    }

    @Override
    @Transactional
    public void deleteSkills(List<String> resourceIds) {
        skillVersionService.deleteAllVersionsByResourceIds(resourceIds);
        skillRepository.deleteByResourceIdIn(resourceIds);
    }

    @Override
    public void updateSkill(SkillUpdateRequest req) {
        SkillEntity entity = skillRepository.findByResourceId(req.getResourceId())
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_NOT_FOUND));

        if (req.getName() != null) entity.setName(req.getName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());

        skillRepository.save(entity);
    }

    @Override
    public SkillInfoBase getSkillInfo(String resourceId) {
        return skillRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(SkillError.SKILL_NOT_FOUND));
    }

    @Override
    public List<SkillMetaInfoResponse> listPublishedSkillsMeta(List<String> resourceIds) {
        return skillRepository.findByResourceIdInAndVersionGreaterThan(resourceIds, 0)
                .stream()
                .map(entity -> BeanUtil.copyProperties(entity, SkillMetaInfoResponse.class))
                .toList();
    }
}
