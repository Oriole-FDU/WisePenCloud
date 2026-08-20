package com.oriole.wisepen.ai.asset.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.domain.dto.res.AIResourceMetaInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.AgentInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.AgentVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.domain.entity.AgentVersionBundleEntity;
import com.oriole.wisepen.ai.asset.exception.AIResourceError;
import com.oriole.wisepen.ai.asset.service.impl.AgentServiceImpl;
import com.oriole.wisepen.ai.asset.service.impl.AgentVersionServiceImpl;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/agent")
@RequiredArgsConstructor
public class InternalAgentController {

    private final AgentServiceImpl agentService;
    private final AgentVersionServiceImpl agentVersionService;

    @GetMapping("/getAgentByResourceId")
    public R<AgentInfoResponse> getAgentByResourceId(@RequestParam String resourceId, @RequestParam(required = false) Integer agentVersion) {
        AIResourceMetaInfoResponse agent = agentService.getAIResourceInfo(resourceId);
        if (agentVersion == null) agentVersion = agent.getVersion();
        if (agentVersion <= 0) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND);
        }
        AgentInfoResponse response = BeanUtil.copyProperties(agent, AgentInfoResponse.class);
        AgentVersionBundleEntity bundle = agentVersionService.getVersionBundle(resourceId, agentVersion);
        response.setAgentVersionBundle(BeanUtil.copyProperties(bundle, AgentVersionBundleInfoResponse.class));
        return R.ok(response);
    }
}