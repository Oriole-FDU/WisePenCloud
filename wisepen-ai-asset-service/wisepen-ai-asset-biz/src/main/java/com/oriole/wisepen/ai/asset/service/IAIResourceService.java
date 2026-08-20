package com.oriole.wisepen.ai.asset.service;

import com.oriole.wisepen.ai.asset.domain.base.AIResourceInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AIResourceCreateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AIResourceForkRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AIResourceUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AIResourceMetaInfoResponse;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;

import java.util.List;
import java.util.Map;

public interface IAIResourceService {

    String createAIResource(AIResourceCreateRequest req, String userId, Map<Long, GroupRoleType> groupRoles);

    String forkAIResource(AIResourceForkRequest req, String forkedResourceOwnerId);

    void deleteAIResources(List<String> resourceIds);

    void updateAIResourceInfo(AIResourceUpdateRequest req);

    AIResourceMetaInfoResponse getAIResourceInfo(String resourceId);

    List<AIResourceMetaInfoResponse> listPublishedAIResourcesMeta(List<String> resourceIds);

}
