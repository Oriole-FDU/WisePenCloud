package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceUpdateDTO;
import com.oriole.wisepen.resource.service.IResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/resource")
@RequiredArgsConstructor
public class InternalResourceItemController {

    private final IResourceService resourceService;

    // 注册/新增资源摘要
    @PostMapping("/addRes")
    public R<String> createResource(@Validated @RequestBody ResourceCreateDTO dto) {
        String resourceId = resourceService.createResourceItem(dto);
        return R.ok(resourceId);
    }

    // 删除资源摘要
    @PostMapping("/deleteRes")
    public R<Void> removeResource(@RequestParam("resourceId") String resourceId) {
        resourceService.removeResourceItem(resourceId);
        return R.ok();
    }

    // 同步修改资源属性

    @PostMapping("/changeResAttr")
    public R<Void> updateAttributes(@Validated @RequestBody ResourceUpdateDTO dto) {
        resourceService.updateResourceAttributes(dto);
        return R.ok();
    }

    // 内部鉴权接口，供下游微服务在执行敏感操作（如：导出PDF、分享链接）前进行硬核鉴权
    @PostMapping("/checkResPermission")
    public R<Boolean> checkResPermission(@Validated @RequestBody ResourceCheckPermissionDTO dto) {
        Boolean hasPermission = resourceService.checkPermission(dto);
        return R.ok(hasPermission);
    }

}
