package com.oriole.wisepen.ai.asset.service;

import com.oriole.wisepen.ai.asset.domain.dto.req.AgentCreateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AgentInfoResponse;

import java.util.List;

public interface IAgentService {

    String createAgent(AgentCreateRequest req, String userId);

    void deleteAgents(List<String> resourceIds);

    void updateAgent(AgentUpdateRequest req);

    AgentInfoResponse getAgentInfo(String resourceId);
}
