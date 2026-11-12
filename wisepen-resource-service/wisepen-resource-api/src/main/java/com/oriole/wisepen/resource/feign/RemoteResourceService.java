package com.oriole.wisepen.resource.feign;

import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.dto.req.PersonalFolderEnsureReqDTO;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 提供给其他微服务的权限 RPC 接口
 */
@FeignClient(contextId = "remoteResourceService", value = "wisepen-resource-service")
public interface RemoteResourceService {

    @PostMapping("/internal/resource/addRes")
    R<String> createResource(@RequestBody ResourceCreateReqDTO dto);

    @PostMapping("/internal/resource/changeResAttr")
    R<Void> updateAttributes(@RequestBody ResourceUpdateReqDTO dto);

    @PostMapping("/internal/resource/getResourceInfo")
    R<ResourceItemResponse> getResourceInfo(@RequestBody ResourceInfoGetReqDTO dto);

    @PostMapping("/internal/resource/listResourceBaseInfo")
    R<List<ResourceItemInfoResDTO>> listResourceBaseInfo(@RequestBody List<String> resourceIds);

    @PostMapping("/internal/resource/checkResPermission")
    R<ResourceCheckPermissionResDTO> checkResPermission(@RequestBody ResourceCheckPermissionReqDTO dto);

    @PostMapping("/internal/resource/listAiBillingApprovedGroupIds")
    R<List<String>> listAiBillingApprovedGroupIds(@RequestParam("resourceId") String resourceId);

    @PostMapping("/internal/resource/ensurePersonalFolder")
    R<String> ensurePersonalFolder(@RequestBody PersonalFolderEnsureReqDTO dto);

    @PostMapping("/internal/resource/dissolveGroup")
    R<Void> dissolveGroup(@RequestParam("groupId") Long groupId);

}
