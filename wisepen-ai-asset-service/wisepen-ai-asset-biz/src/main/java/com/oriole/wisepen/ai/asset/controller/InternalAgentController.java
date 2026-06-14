package com.oriole.wisepen.ai.asset.controller;

import com.oriole.wisepen.ai.asset.domain.dto.res.AgentInfoResponse;
import com.oriole.wisepen.ai.asset.exception.AgentError;
import com.oriole.wisepen.ai.asset.service.IAgentService;
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

    private final IAgentService agentService;

    @GetMapping("/getAgentByResourceId")
    public R<AgentInfoResponse> getPublishedAgentByResourceId(@RequestParam String resourceId) {
        AgentInfoResponse agent = agentService.getAgentInfo(resourceId);
        if (agent.getVersion() == null || agent.getVersion() <= 0) {
            throw new ServiceException(AgentError.AGENT_VERSION_NOT_FOUND);
        }
        return R.ok(agent);
    }
}
