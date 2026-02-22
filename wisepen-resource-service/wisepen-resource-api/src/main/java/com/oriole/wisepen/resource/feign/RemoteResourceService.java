package com.oriole.wisepen.resource.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceUpdateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 提供给其他微服务的权限 RPC 接口
 */
@FeignClient(contextId = "remoteResourceService", value = "wisepen-resource-service")
public interface RemoteResourceService {

    @PostMapping("/internal/resource/addRes")
    R<String> createResource(@RequestBody ResourceCreateDTO dto);

    @PostMapping("/internal/resource/deleteRes")
    R<Void> removeResource(@RequestParam("resourceId") String resourceId);

    @PostMapping("/internal/resource/changeResAttr")
    R<Void> updateAttributes(@RequestBody ResourceUpdateDTO dto);

    @PostMapping("/internal/resource/checkResPermission")
    R<Boolean> checkResPermission(@RequestBody ResourceCheckPermissionDTO dto);

}