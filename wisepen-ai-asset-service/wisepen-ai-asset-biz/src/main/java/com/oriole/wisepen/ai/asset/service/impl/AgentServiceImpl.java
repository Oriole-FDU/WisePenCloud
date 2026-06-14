package com.oriole.wisepen.ai.asset.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentCreateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AgentInfoResponse;
import com.oriole.wisepen.ai.asset.domain.entity.AgentEntity;
import com.oriole.wisepen.ai.asset.domain.entity.AgentVersionBundleEntity;
import com.oriole.wisepen.ai.asset.exception.AgentError;
import com.oriole.wisepen.ai.asset.repository.AgentRepository;
import com.oriole.wisepen.ai.asset.service.IAgentService;
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
public class AgentServiceImpl implements IAgentService {

    private final AgentRepository agentRepository;
    private final IVersionService<AgentVersionBundleEntity> agentVersionService;
    private final RemoteResourceService remoteResourceService;

    @Override
    public String createAgent(AgentCreateRequest req, String userId) {
        String resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                .resourceName(req.getTitle())
                .resourceType(ResourceType.AGENT)
                .ownerId(userId)
                .build()).getData();
        if (!StringUtils.hasText(resourceId)) {
            throw new ServiceException(AgentError.AGENT_RESOURCE_REGISTER_FAILED);
        }

        AgentEntity entity = AgentEntity.builder()
                .resourceId(resourceId)
                .name(req.getName() == null ? "" : req.getName())
                .description(req.getDescription() == null ? "" : req.getDescription())
                .version(0)
                .build();
        agentRepository.save(entity);
        // 直接新建首份草案(1)
        agentVersionService.createDraft(resourceId, 1);
        return resourceId;
    }

    @Override
    @Transactional
    public void deleteAgents(List<String> resourceIds) {
        agentVersionService.deleteAllVersionsByResourceIds(resourceIds);
        agentRepository.deleteByResourceIdIn(resourceIds);
    }

    @Override
    public void updateAgent(AgentUpdateRequest req) {
        AgentEntity entity = agentRepository.findByResourceId(req.getResourceId())
                .orElseThrow(() -> new ServiceException(AgentError.AGENT_NOT_FOUND));

        if (req.getName() != null) entity.setName(req.getName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());

        agentRepository.save(entity);
    }

    @Override
    public AgentInfoResponse getAgentInfo(String resourceId) {
        AgentEntity entity = agentRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(AgentError.AGENT_NOT_FOUND));
        return BeanUtil.copyProperties(entity, AgentInfoResponse.class);
    }
}
