package com.oriole.wisepen.generic.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.generic.resource.api.constant.GenericResourceValidationMsg;
import com.oriole.wisepen.generic.resource.api.domain.dto.req.GenericResourceUploadInitRequest;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceFileInfoResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceInfoResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceSourceFileDownloadResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadInitResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadStatusResponse;
import com.oriole.wisepen.generic.resource.exception.GenericResourceError;
import com.oriole.wisepen.generic.resource.service.IGenericResourceService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceInfoGetReqDTO;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "通用文件资源", description = "无需解析预览的文件资源上传、状态查询和下载")
@RestController
@RequestMapping("/genericResource")
@RequiredArgsConstructor
@CheckRole
@Validated
public class GenericResourceController {

    private final IGenericResourceService genericResourceService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "初始化通用资源上传",
            description = """
                    - 用途：为当前用户创建无需解析预览的文件资源上传任务，并申请对象存储直传凭证。
                    - 请求：filename 为展示文件名；extension 可选，未传时从 filename 解析；md5 用于秒传判定；expectedSize 为预期文件大小；mountTargetTagId 可选，用于上传完成后挂载资源路径。
                    - 约束：当前用户必须已登录；文件类型不能属于文档、笔记、AI 资产等专属资源服务；未识别扩展名会归为 UNKNOWN。
                    - 处理：创建上传任务并向文件存储服务申请私有对象上传 URL；非秒传时等待上传完成事件后注册资源；秒传时同步补偿存储记录并立即注册资源。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；文件类型不由通用资源服务托管 -> GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE；存储服务申请上传凭证失败 -> GenericResourceError.GENERIC_RESOURCE_UPLOAD_URL_APPLY_FAILED；资源注册失败 -> GenericResourceError.GENERIC_RESOURCE_REGISTER_FAILED。
                    - 响应：返回 uploadId、objectKey、上传凭证、资源类型、是否秒传和当前上传状态；秒传成功时同时返回 resourceId。
                    """
    )
    @Log(title = "初始化通用资源上传", businessType = BusinessType.INSERT)
    @PostMapping("/initUploadGenericResource")
    public R<GenericResourceUploadInitResponse> initUploadGenericResource(@Valid @RequestBody GenericResourceUploadInitRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        return R.ok(genericResourceService.initUploadGenericResource(request, userId, groupRoles));
    }

    @Operation(
            summary = "同步通用资源上传状态",
            description = """
                    - 用途：主动同步当前用户发起的通用资源上传任务，并在存储状态已就绪但事件尚未消费时补偿注册资源。
                    - 请求：uploadId 指定上传任务。
                    - 约束：当前用户必须是该上传任务创建者；目标上传任务必须存在。
                    - 处理：读取上传任务状态；若任务尚未 AVAILABLE，会查询文件存储记录，确认对象已可用后注册资源并更新状态；不重新申请上传 URL，不重复上传文件。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；上传任务不存在 -> GenericResourceError.GENERIC_RESOURCE_UPLOAD_NOT_FOUND；当前用户不是上传者 -> GenericResourceError.GENERIC_RESOURCE_PERMISSION_DENIED；存储状态查询失败 -> GenericResourceError.GENERIC_RESOURCE_STORAGE_STATUS_GET_FAILED；资源注册失败 -> GenericResourceError.GENERIC_RESOURCE_REGISTER_FAILED。
                    - 响应：返回上传任务、资源类型、objectKey、资源 ID 和当前状态。
                    """
    )
    @Log(title = "同步通用资源上传状态", businessType = BusinessType.UPDATE)
    @PostMapping("/syncGenericResourceUploadStatus")
    public R<GenericResourceUploadStatusResponse> syncGenericResourceUploadStatus(
            @NotBlank(message = GenericResourceValidationMsg.UPLOAD_ID_EMPTY) @RequestParam String uploadId) {
        return R.ok(genericResourceService.syncGenericResourceUploadStatus(uploadId, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "获取通用资源信息",
            description = """
                    - 用途：获取通用资源详情和原始文件元信息，用于资源详情页展示。
                    - 请求：resourceId 指定通用资源；targetVersion 可选，用于 Market 版本限定权限裁决。
                    - 约束：当前用户必须已登录，且必须通过资源服务的资源详情权限校验；目标资源必须由通用资源服务托管。
                    - 处理：通过资源服务获取资源详情和当前用户可执行动作，再读取通用资源文件元信息并组合响应；不生成下载地址，不读取文件内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；资源无查看权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；通用资源不存在 -> GenericResourceError.GENERIC_RESOURCE_NOT_FOUND。
                    - 响应：返回资源信息与通用资源文件信息。
                    """
    )
    @GetMapping("/getGenericResourceInfo")
    public R<GenericResourceInfoResponse> getGenericResourceInfo(
            @NotBlank(message = GenericResourceValidationMsg.RESOURCE_ID_EMPTY) @RequestParam String resourceId,
            @RequestParam(value = "targetVersion", required = false) Integer targetVersion) {
        // 若无权限将抛出异常，此处无需重复鉴权。
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap(), targetVersion
        )).getData();
        GenericResourceFileInfoResponse genericResourceInfo = genericResourceService.getGenericResourceInfo(resourceId);
        return R.ok(GenericResourceInfoResponse.builder()
                .resourceInfo(resourceInfo)
                .genericResourceInfo(genericResourceInfo)
                .build());
    }

    @Operation(
            summary = "获取通用资源源文件下载地址",
            description = """
                    - 用途：为具备 VIEW 权限的用户签发通用资源源文件的临时下载地址。
                    - 请求：resourceId 指定目标资源；durationSeconds 可选，用于指定下载 URL 有效期。
                    - 约束：当前用户必须已登录并拥有目标资源 VIEW 动作；目标资源必须由通用资源服务托管且已上传完成。
                    - 处理：先调用资源服务校验 VIEW 权限，再读取通用资源文件记录，并向文件存储服务申请源文件防盗链下载 URL；不代理文件内容，不生成水印文件或预览文件。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 VIEW 权限 -> GenericResourceError.GENERIC_RESOURCE_PERMISSION_DENIED；资源不存在 -> GenericResourceError.GENERIC_RESOURCE_NOT_FOUND；资源尚未就绪 -> GenericResourceError.GENERIC_RESOURCE_NOT_READY；存储服务申请下载地址失败 -> GenericResourceError.GENERIC_RESOURCE_DOWNLOAD_URL_APPLY_FAILED。
                    - 响应：返回资源名称、类型、扩展名、大小和临时下载地址。
                    """
    )
    @GetMapping("/downloadGenericResourceSourceFile")
    public R<GenericResourceSourceFileDownloadResponse> downloadGenericResourceSourceFile(
            @NotBlank(message = GenericResourceValidationMsg.RESOURCE_ID_EMPTY) @RequestParam String resourceId,
            @RequestParam(value = "durationSeconds", required = false) Long durationSeconds) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId)
                .userId(userId)
                .groupRoles(groupRoles)
                .build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(GenericResourceError.GENERIC_RESOURCE_PERMISSION_DENIED);
        }
        return R.ok(genericResourceService.createSourceFileDownloadTicket(resourceId, durationSeconds));
    }
}
