package com.oriole.wisepen.resource.feign;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "内部资源服务", description = "提供给其他微服务的权限与资源 Feign 接口")
@FeignClient(contextId = "remoteResourceService", value = "wisepen-resource-service")
public interface RemoteResourceService {

    @Operation(summary = "注册/创建资源", description = "注册用户资源")
    @PostMapping("/internal/resource/addRes")
    R<String> createResource(@RequestBody ResourceCreateDTO dto);

    @Operation(summary = "移除资源", description = "移除用户资源")
    @PostMapping("/internal/resource/deleteRes")
    R<Void> removeResource(@RequestParam("resourceId") String resourceId);

    @Operation(summary = "更新资源属性", description = "更新已有资源的大小等元信息")
    @PostMapping("/internal/resource/changeResAttr")
    R<Void> updateAttributes(@RequestBody ResourceUpdateDTO dto);

    @Operation(summary = "检查资源权限", description = "校验用户对某资源是否有访问权限")
    @PostMapping("/internal/resource/checkResPermission")
    R<Boolean> checkResPermission(@RequestBody ResourceCheckPermissionDTO dto);

}