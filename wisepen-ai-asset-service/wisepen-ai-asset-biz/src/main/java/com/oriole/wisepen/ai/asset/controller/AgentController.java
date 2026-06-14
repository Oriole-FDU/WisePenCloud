package com.oriole.wisepen.ai.asset.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentCreateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentSpecUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AgentVersionPublishRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AgentResourceInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.AgentVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.entity.AgentVersionBundleEntity;
import com.oriole.wisepen.ai.asset.exception.AgentError;
import com.oriole.wisepen.ai.asset.service.IAgentService;
import com.oriole.wisepen.ai.asset.service.impl.VersionServiceImpl;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceInfoGetReqDTO;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "智能体资产", description = "智能体资产创建、资料维护、版本发布和草稿文件管理")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@CheckLogin
public class AgentController {

    private final IAgentService agentService;
    // 用具体类型注入 agent 版本服务 Bean，updateDraftSpec 不在 IVersionService 接口上
    private final VersionServiceImpl<AgentVersionBundleEntity> agentVersionService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "创建智能体资产",
            description = """
                    - 用途：为当前用户创建一个可管理和发布的智能体资产。
                    - 请求：title 为资源展示标题；name 和 description 为智能体元信息，可为空。
                    - 约束：当前用户必须已登录；title 必须是可用于展示的资源标题。
                    - 处理：调用资源服务注册 AGENT 类型资源，以当前用户作为所有者；创建智能体主档并初始化首个草稿版本 1；不上传文件，也不发布版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源注册失败 -> AgentError.AGENT_RESOURCE_REGISTER_FAILED。
                    - 响应：返回智能体资产资源 ID。
                    """
    )
    @Log(title = "创建 Agent", businessType = BusinessType.INSERT)
    @PostMapping("/createAgent")
    public R<String> createAgent(@Validated @RequestBody AgentCreateRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        String resourceId = agentService.createAgent(request, userId);
        return R.ok(resourceId);
    }

    @Operation(
            summary = "更新智能体资产信息",
            description = """
                    - 用途：维护智能体资产的名称和描述信息。
                    - 请求：resourceId 指定智能体资产；name 和 description 为空时不更新对应字段。
                    - 约束：当前用户必须是资源所有者；目标智能体资产必须存在。
                    - 处理：按非空字段更新智能体主档元信息；不修改资源标题、草稿文件、版本号或发布状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> AgentError.AGENT_PERMISSION_DENIED；智能体不存在 -> AgentError.AGENT_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "更新 Agent 信息", businessType = BusinessType.UPDATE)
    @PostMapping("/changeAgentInfo")
    public R<Void> updateAgentInfo(@Validated @RequestBody AgentUpdateRequest request) {
        assertAgentOwner(request.getResourceId());
        agentService.updateAgent(request);
        return R.ok();
    }

    @Operation(
            summary = "获取智能体资产信息",
            description = """
                    - 用途：获取智能体资源详情和智能体资产主档信息。
                    - 请求：resourceId 指定智能体资产资源。
                    - 约束：当前用户必须已登录，且必须通过资源服务的资源详情权限校验；目标智能体资产必须存在。
                    - 处理：通过资源服务获取资源详情和当前用户动作集合，再读取智能体主档信息并组合响应；不读取版本快照。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；资源无查看权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；智能体不存在 -> AgentError.AGENT_NOT_FOUND。
                    - 响应：返回资源信息与智能体资产信息。
                    """
    )
    @PostMapping("/getAgentInfo")
    public R<AgentResourceInfoResponse> getAgentInfo(@RequestParam String resourceId) {
        // 若无权限将抛出异常，此处无需重复鉴权
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap()
        )).getData();
        AgentResourceInfoResponse agentResourceInfoResponse = AgentResourceInfoResponse.builder()
                .resourceInfo(resourceInfo)
                .agentInfo(agentService.getAgentInfo(resourceId))
                .build();
        return R.ok(agentResourceInfoResponse);
    }

    @Operation(
            summary = "更新智能体草稿运行配置",
            description = """
                    - 用途：维护智能体草稿版本的运行配置（系统提示词与模型、工具、记忆策略），发布前必须配置完成。
                    - 请求：resourceId 指定智能体资产；draftVersion 指定草稿版本；spec 为完整运行配置，整体覆盖草稿中现有配置。
                    - 约束：当前用户必须是资源所有者；目标版本必须存在且是 DRAFT。
                    - 处理：将草稿版本的运行配置整体替换为请求中的 spec；不修改草稿文件、版本号或发布状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> AgentError.AGENT_PERMISSION_DENIED；版本不存在 -> AgentError.AGENT_VERSION_NOT_FOUND；版本不是草稿 -> AgentError.CANNOT_OPERATE_NON_DRAFT_AGENT_VERSION。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "更新 Agent 草稿配置", businessType = BusinessType.UPDATE)
    @PostMapping("/updateAgentDraftSpec")
    public R<Void> updateAgentDraftSpec(@Validated @RequestBody AgentSpecUpdateRequest request) {
        assertAgentOwner(request.getResourceId());
        agentVersionService.updateDraftSpec(request);
        return R.ok();
    }

    @Operation(
            summary = "获取智能体版本包信息",
            description = """
                    - 用途：查询智能体资产指定版本或当前已发布版本的运行配置与文件快照。
                    - 请求：resourceId 指定智能体资产；version 为空时使用智能体主档当前发布版本。
                    - 约束：当前用户必须是资源所有者；智能体资产和目标版本必须存在。
                    - 处理：确定目标版本后读取版本记录、运行配置 spec 及其资产文件列表；不生成下载地址，不改变草稿或发布状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> AgentError.AGENT_PERMISSION_DENIED；智能体不存在 -> AgentError.AGENT_NOT_FOUND；版本不存在 -> AgentError.AGENT_VERSION_NOT_FOUND。
                    - 响应：返回智能体版本包信息、运行配置和资产文件元数据。
                    """
    )
    @PostMapping("/getAgentVersionBundleInfo")
    public R<AgentVersionBundleInfoResponse> getAgentVersionBundleInfo(@RequestParam String resourceId, Integer version) {
        assertAgentOwner(resourceId);
        AgentVersionBundleEntity bundle = agentVersionService.getBundle(resourceId, version);
        return R.ok(BeanUtil.copyProperties(bundle, AgentVersionBundleInfoResponse.class));
    }

    @Operation(
            summary = "发布智能体版本",
            description = """
                    - 用途：将智能体资产的当前草稿版本确认为正式发布版本。
                    - 请求：resourceId 指定智能体资产。
                    - 约束：当前用户必须是资源所有者；目标版本必须是 DRAFT；运行配置中的系统提示词不能为空；所有草稿资产都必须处于可用状态。
                    - 处理：将草稿版本标记为 PUBLISHED，更新智能体主档当前版本号，并创建下一版草稿；不复制文件，也不修改已发布版本内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> AgentError.AGENT_PERMISSION_DENIED；版本不存在 -> AgentError.AGENT_VERSION_NOT_FOUND；版本不是草稿 -> AgentError.CANNOT_OPERATE_NON_DRAFT_AGENT_VERSION；系统提示词为空 -> AgentError.AGENT_PROMPT_REQUIRED；存在上传中的资产 -> AgentError.AGENT_ASSET_NOT_READY。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "发布 Agent 版本", businessType = BusinessType.UPDATE)
    @PostMapping("/publishAgentVersion")
    public R<Void> publishAgentVersion(@Validated @RequestBody AgentVersionPublishRequest request) {
        assertAgentOwner(request.getResourceId());
        agentVersionService.publishVersion(request.getResourceId());
        return R.ok();
    }

    @Operation(
            summary = "初始化智能体文件上传",
            description = """
                    - 用途：为智能体资产草稿版本新增或替换一批资产文件，并申请对象存储上传凭证。
                    - 请求：resourceId 指定智能体资产；draftVersion 指定草稿版本；assets 中的 path、name、assetResourceType、md5、expectedSize 描述待上传文件。
                    - 约束：当前用户必须是资源所有者；目标版本必须是 DRAFT；path 必须以 / 开头且不能包含非法目录跳转；name 不能包含路径分隔符；资产列表不能为空。
                    - 处理：在草稿版本中查找或创建资产条目，向文件存储服务申请上传 URL 或秒传，更新资产 objectKey、大小和上传状态；被替换且不再被任何版本引用的旧文件会发布删除事件；不发布草稿版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> AgentError.AGENT_PERMISSION_DENIED；版本不存在 -> AgentError.AGENT_VERSION_NOT_FOUND；版本不是草稿 -> AgentError.CANNOT_OPERATE_NON_DRAFT_AGENT_VERSION；资产路径非法 -> AgentError.AGENT_ASSET_PATH_INVALID；存储上传凭证申请失败 -> AgentError.AGENT_ASSET_UPLOAD_URL_APPLY_FAILED。
                    - 响应：返回每个资产的 assetId、路径、文件名、objectKey、上传凭证和是否秒传。
                    """
    )
    @Log(title = "上传 Agent 资源", businessType = BusinessType.INSERT)
    @PostMapping("/initUploadAgentAssets")
    public R<AssetUploadInitResponse> initUploadAgentAssets(@Validated @RequestBody AssetUploadInitRequest request) {
        assertAgentOwner(request.getResourceId());
        AssetUploadInitResponse assetUploadInitResponse = agentVersionService.initUploadAssets(request);
        return R.ok(assetUploadInitResponse);
    }

    @Operation(
            summary = "删除智能体草稿文件",
            description = """
                    - 用途：从智能体资产草稿版本中移除一批资产文件。
                    - 请求：resourceId 指定智能体资产；draftVersion 指定草稿版本；assetIds 为待删除资产 ID 列表。
                    - 约束：当前用户必须是资源所有者；目标版本必须是 DRAFT；assetIds 不能为空。
                    - 处理：从草稿版本中移除匹配的主文件或普通资产文件，并对不再被任何版本引用的 objectKey 发布文件删除事件；不影响已发布版本中仍被引用的文件。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> AgentError.AGENT_PERMISSION_DENIED；版本不存在 -> AgentError.AGENT_VERSION_NOT_FOUND；版本不是草稿 -> AgentError.CANNOT_OPERATE_NON_DRAFT_AGENT_VERSION。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "删除 Agent 资源", businessType = BusinessType.DELETE)
    @PostMapping("/deleteAgentAssets")
    public R<Void> deleteAgentAssets(@Validated @RequestBody AssetDeleteRequest request) {
        assertAgentOwner(request.getResourceId());
        agentVersionService.deleteAssets(request);
        return R.ok();
    }

    private void assertAgentOwner(String resourceId) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId)
                .userId(SecurityContextHolder.getUserId())
                .groupRoles(SecurityContextHolder.getGroupRoleMap())
                .build()).getData();
        if (permission == null || permission.getResourceAccessRole() != ResourceAccessRole.OWNER) {
            throw new ServiceException(AgentError.AGENT_PERMISSION_DENIED);
        }
    }
}
