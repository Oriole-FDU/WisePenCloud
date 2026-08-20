package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.resource.service.IGroupResService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.ITagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "内部 - 资源", description = "供业务微服务注册资源、更新资源属性、查询资源信息和校验资源权限")
@RestController
@RequestMapping("/internal/resource")
@RequiredArgsConstructor
public class InternalResourceItemController implements RemoteResourceService {

    // 内部 Feign 接口，不打 @Log。被调用方（Document/User Controller）负责在自己的入口处审计。
    private final IResourceService resourceService;
    private final IGroupResService groupResService;
    private final ITagService tagService;

    // 注册/新增资源摘要
    @Operation(
            summary = "内部注册资源",
            description = """
                    - 用途：供文档、文件等业务服务在完成资源前置创建后登记资源中心记录。
                    - 请求：请求体携带资源 ID、所有者、资源类型、资源名称、可选路径标签和上传初始化时的小组角色快照等资源摘要字段。
                    - 约束：调用方必须通过内部服务调用边界；资源所有者和路径标签需要与业务上下文一致；小组路径标签必须在角色快照中存在对应成员身份。
                    - 处理：创建资源记录；个人路径标签绑定到所有者个人空间，普通小组标签同时绑定到所有者个人 .Shared 与目标小组标签；初始化互动统计并同步资源搜索元数据；若标签绑定失败会回滚资源记录。
                    - 失败：个人标签根节点或指定路径标签不存在 -> ResourceError.TAG_NODE_NOT_FOUND；路径标签数量不唯一 -> ResourceError.CANNOT_BIND_RESOURCE_TO_MULTIPLE_PATH_NODES；路径标签未放在首位 -> ResourceError.CANNOT_PLACE_RESOURCE_PATH_TAG_AFTER_TAGS；搜索同步失败 -> ResourceError.RESOURCE_SEARCH_FAILED。
                    - 响应：返回新注册资源 ID。
                    """
    )
    @Log(title = "内部注册资源", businessType = BusinessType.INSERT)
    @PostMapping("/addRes")
    public R<String> createResource(@Validated @RequestBody ResourceCreateReqDTO dto) {
        String resourceId = resourceService.createResourceItem(dto);
        return R.ok(resourceId);
    }

    // 同步修改资源属性
    @Operation(
            summary = "内部更新资源属性",
            description = """
                    - 用途：供资源实际承载服务同步资源大小、名称、元数据等属性变化。
                    - 请求：请求体携带 resourceId 和需要更新的资源属性字段；未传字段不覆盖原值。
                    - 约束：调用方必须通过内部服务调用边界；目标资源 ID 由上游业务流程提供。
                    - 处理：按非空字段更新资源属性；资源不存在时服务层记录跳过日志，不创建新资源。
                    - 失败：底层存储更新发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/changeResAttr")
    public R<Void> updateAttributes(@Validated @RequestBody ResourceUpdateReqDTO dto) {
        resourceService.updateResourceAttributes(dto);
        return R.ok();
    }

    @Operation(
            summary = "内部获取资源信息",
            description = """
                    - 用途：供下游服务在展示、导出或访问资源前获取资源详情和当前用户可用动作。
                    - 请求：请求参数携带 resourceId、userId 和用户小组角色上下文。
                    - 约束：目标资源必须存在；请求用户必须拥有资源 VIEW 权限。
                    - 处理：计算资源所有者、指定用户特权和小组 ACL 得到当前用户动作集合，补充资源互动统计和所有者展示信息；不修改资源记录或权限配置。
                    - 失败：资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；用户没有 VIEW 权限 -> ResourceError.RESOURCE_PERMISSION_DENIED。
                    - 响应：返回资源详情、当前用户可执行动作、互动统计和所有者展示信息。
                    """
    )
    @PostMapping("/getResourceInfo")
    public R<ResourceItemResponse> getResourceInfo(ResourceInfoGetReqDTO dto) {
        ResourceItemResponse response = resourceService.getResourceInfo(dto);
        return R.ok(response);
    }

    @Operation(
            summary = "内部批量获取资源基础信息",
            description = """
                    - 用途：供小组看板等内部场景按资源 ID 批量补全资源展示字段。
                    - 请求：请求体为 resourceId 列表；空列表按空结果处理。
                    - 约束：调用方必须通过内部服务调用边界；本接口不做用户可见性校验；已删除资源不会返回；不补充 ownerInfo。
                    - 处理：批量查询资源主记录，返回 resourceId、resourceName、resourceType、ownerId、preview 和 size；不读取交互明细，不修改资源数据。
                    - 失败：底层存储查询发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回资源基础信息列表，结果数量可能少于请求 ID 数量。
                    """
    )
    @PostMapping("/listResourceBaseInfo")
    public R<List<ResourceItemInfoResDTO>> listResourceBaseInfo(@RequestBody List<String> resourceIds) {
        return R.ok(resourceService.listResourceBaseInfo(resourceIds));
    }

    // 内部鉴权接口，供下游微服务在执行敏感操作（如：导出PDF、分享链接）前进行硬核鉴权
    @Operation(
            summary = "内部校验资源权限",
            description = """
                    - 用途：供下游服务在导出、分享、编辑等敏感操作前校验用户对资源的访问角色和动作权限。
                    - 请求：请求参数携带 resourceId、userId 和用户小组角色上下文。
                    - 约束：调用方必须通过内部服务调用边界；资源不存在或已不在业务表中时视为无权限。
                    - 处理：按资源所有者、指定用户特权、小组管理员角色和标签 ACL 计算最终访问角色与动作集合；不写入资源或用户互动数据。
                    - 失败：权限计算依赖数据异常 -> CommonError.INTERNAL_ERROR；资源不存在按无权限响应处理。
                    - 响应：返回访问角色、权限来源小组和可执行动作集合。
                    """
    )
    @PostMapping("/checkResPermission")
    public R<ResourceCheckPermissionResDTO> checkResPermission(@RequestBody ResourceCheckPermissionReqDTO dto) {
        ResourceCheckPermissionResDTO hasPermission = resourceService.checkPermission(dto);
        return R.ok(hasPermission);
    }

    // 小组解散：软删除 Tag 树与配置
    @Operation(
            summary = "内部清理已解散小组资源",
            description = """
                    - 用途：供用户服务在小组解散后清理资源服务中的小组标签树和资源配置。
                    - 请求：groupId 指定已解散小组。
                    - 约束：调用方必须通过内部服务调用边界；groupId 必须来自已确认解散的小组。
                    - 处理：软删除该小组全部标签节点，并软删除小组资源配置；不直接删除资源本体或个人标签空间。
                    - 失败：标签或配置清理发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/dissolveGroup")
    public R<Void> dissolveGroup(@RequestParam("groupId") Long groupId) {
        tagService.softRemoveAllTagByGroupId(groupId.toString());
        groupResService.softRemoveGroupResConfigByGroupId(groupId.toString());
        return R.ok();
    }
}
