package com.oriole.wisepen.ai.asset.controller;

import com.oriole.wisepen.ai.asset.domain.base.SkillInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetDeleteRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.AssetUploadInitRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillCreateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillUpdateRequest;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillVersionPublishRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.AssetUploadInitResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillResourceInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.exception.SkillError;
import com.oriole.wisepen.ai.asset.service.ISkillService;
import com.oriole.wisepen.ai.asset.service.IVersionService;
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

@Tag(name = "技能资产", description = "技能资产创建、资料维护、版本发布和草稿文件管理")
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
@CheckLogin
public class SkillController {

    private final ISkillService skillService;
    private final IVersionService skillVersionService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "创建技能资产",
            description = """
                    - 用途：为当前用户创建一个可管理和发布的技能资产。
                    - 请求：title 为资源展示标题；name、description 和 sourceType 为技能资产元信息，sourceType 为空时按 MANUAL 处理。
                    - 约束：当前用户必须已登录；title 必须是可用于展示的资源标题。
                    - 处理：调用资源服务注册 SKILL 类型资源，以当前用户作为所有者；创建技能主档并初始化首个草稿版本 1；不上传技能文件，也不发布版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源注册失败或技能主档落库失败 -> SkillError.SKILL_RESOURCE_REGISTER_FAILED。
                    - 响应：返回技能资产资源 ID。
                    """
    )
    @Log(title = "创建 Skill", businessType = BusinessType.INSERT)
    @PostMapping("/createSkill")
    public R<String> createSkill(@Validated @RequestBody SkillCreateRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        String resourceId = skillService.createSkill(request, userId);
        return R.ok(resourceId);
    }

    @Operation(
            summary = "更新技能资产信息",
            description = """
                    - 用途：维护技能资产的名称和描述信息。
                    - 请求：resourceId 指定技能资产；name 和 description 为空时不更新对应字段。
                    - 约束：当前用户必须是资源所有者；目标技能资产必须存在。
                    - 处理：按非空字段更新技能主档元信息；不修改资源标题、草稿文件、版本号或发布状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> SkillError.SKILL_PERMISSION_DENIED；技能不存在 -> SkillError.SKILL_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "更新 Skill 信息", businessType = BusinessType.UPDATE)
    @PostMapping("/changeSkillInfo")
    public R<Void> updateSkillInfo(@Validated @RequestBody SkillUpdateRequest request) {
        assertSkillOwner(request.getResourceId());
        skillService.updateSkill(request);
        return R.ok();
    }

    @Operation(
            summary = "获取技能资产信息",
            description = """
                    - 用途：获取技能资源详情和技能资产主档信息。
                    - 请求：resourceId 指定技能资产资源。
                    - 约束：当前用户必须已登录，且必须通过资源服务的资源详情权限校验；目标技能资产必须存在。
                    - 处理：通过资源服务获取资源详情和当前用户动作集合，再读取技能主档信息并组合响应；不读取版本文件快照。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；资源无查看权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；技能不存在 -> SkillError.SKILL_NOT_FOUND。
                    - 响应：返回资源信息与技能资产信息。
                    """
    )
    @PostMapping("/getSkillInfo")
    public R<SkillResourceInfoResponse> getSkillInfo(@RequestParam String resourceId) {
        // 若无权限将抛出异常，此处无需重复鉴权
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap()
        )).getData();
        SkillInfoBase skillInfo = skillService.getSkillInfo(resourceId);
        SkillResourceInfoResponse skillResourceInfoResponse = SkillResourceInfoResponse.builder().resourceInfo(resourceInfo).skillInfo(skillInfo).build();
        return R.ok(skillResourceInfoResponse);
    }

    @Operation(
            summary = "获取技能版本包信息",
            description = """
                    - 用途：查询技能资产指定版本或当前已发布版本的文件快照。
                    - 请求：resourceId 指定技能资产；version 为空时使用技能主档当前发布版本。
                    - 约束：当前用户必须是资源所有者；技能资产和目标版本必须存在。
                    - 处理：确定目标版本后读取版本记录及其资产文件列表；不生成下载地址，不改变草稿或发布状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> SkillError.SKILL_PERMISSION_DENIED；技能不存在 -> SkillError.SKILL_NOT_FOUND；版本不存在 -> SkillError.SKILL_VERSION_NOT_FOUND。
                    - 响应：返回技能版本包信息和资产文件元数据。
                    """
    )
    @PostMapping("/getSkillVersionBundleInfo")
    public R<SkillVersionBundleInfoResponse> getSkillVersionBundleInfo(@RequestParam String resourceId, Integer version) {
        assertSkillOwner(resourceId);
        return R.ok(skillVersionService.getSkillVersionBundle(resourceId, version));
    }

    @Operation(
            summary = "发布技能版本",
            description = """
                    - 用途：将技能资产的指定草稿版本确认为正式发布版本。
                    - 请求：resourceId 指定技能资产；draftVersion 指定待发布草稿版本。
                    - 约束：当前用户必须是资源所有者；目标版本必须是 DRAFT；主技能文件必须存在且已上传完成；所有草稿资产都必须处于可用状态。
                    - 处理：将草稿版本标记为 PUBLISHED，更新技能主档当前版本号，并创建下一版草稿；不复制文件，也不修改已发布版本内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> SkillError.SKILL_PERMISSION_DENIED；版本不存在 -> SkillError.SKILL_VERSION_NOT_FOUND；版本不是草稿 -> SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION；主文件缺失 -> SkillError.SKILL_CORE_ASSET_NOT_FOUND；存在上传中的资产 -> SkillError.SKILL_ASSET_NOT_READY。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "发布 Skill 版本", businessType = BusinessType.UPDATE)
    @PostMapping("/publishSkillVersion")
    public R<Void> publishSkillVersion(@Validated @RequestBody SkillVersionPublishRequest request) {
        assertSkillOwner(request.getResourceId());
        skillVersionService.publishSkillVersion(request);
        return R.ok();
    }

    @Operation(
            summary = "初始化技能文件上传",
            description = """
                    - 用途：为技能资产草稿版本新增或替换一批资产文件，并申请对象存储上传凭证。
                    - 请求：resourceId 指定技能资产；draftVersion 指定草稿版本；assets 中的 path、name、skillAssetResourceType、md5、expectedSize 描述待上传文件。
                    - 约束：当前用户必须是资源所有者；目标版本必须是 DRAFT；path 必须以 / 开头且不能包含非法目录跳转；name 不能包含路径分隔符；资产列表不能为空。
                    - 处理：在草稿版本中查找或创建资产条目，向文件存储服务申请上传 URL 或秒传，更新资产 objectKey、大小和上传状态；被替换且不再被任何版本引用的旧文件会发布删除事件；不发布草稿版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> SkillError.SKILL_PERMISSION_DENIED；版本不存在 -> SkillError.SKILL_VERSION_NOT_FOUND；版本不是草稿 -> SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION；资产路径非法 -> SkillError.SKILL_ASSET_PATH_INVALID；存储上传凭证申请失败 -> SkillError.SKILL_ASSET_UPLOAD_URL_APPLY_FAILED。
                    - 响应：返回每个资产的 assetId、路径、文件名、objectKey、上传凭证和是否秒传。
                    """
    )
    @Log(title = "上传 Skill 资源", businessType = BusinessType.INSERT)
    @PostMapping("/initUploadSkillAssets")
    public R<AssetUploadInitResponse> initUploadSkillAssets(@Validated @RequestBody AssetUploadInitRequest request) {
        assertSkillOwner(request.getResourceId());
        AssetUploadInitResponse skillAssetUploadInitResponse = skillVersionService.initUploadSkillAssets(request);
        return R.ok(skillAssetUploadInitResponse);
    }

    @Operation(
            summary = "删除技能草稿文件",
            description = """
                    - 用途：从技能资产草稿版本中移除一批资产文件。
                    - 请求：resourceId 指定技能资产；draftVersion 指定草稿版本；assetIds 为待删除资产 ID 列表。
                    - 约束：当前用户必须是资源所有者；目标版本必须是 DRAFT；assetIds 不能为空。
                    - 处理：从草稿版本中移除匹配的主文件或普通资产文件，并对不再被任何版本引用的 objectKey 发布文件删除事件；不影响已发布版本中仍被引用的文件。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> SkillError.SKILL_PERMISSION_DENIED；版本不存在 -> SkillError.SKILL_VERSION_NOT_FOUND；版本不是草稿 -> SkillError.CANNOT_OPERATE_NON_DRAFT_SKILL_VERSION。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "Delete Skill asset", businessType = BusinessType.DELETE)
    @PostMapping("/deleteSkillAssets")
    public R<Void> deleteSkillAssets(@Validated @RequestBody AssetDeleteRequest request) {
        assertSkillOwner(request.getResourceId());
        skillVersionService.deleteSkillAssets(request);
        return R.ok();
    }

    private void assertSkillOwner(String resourceId) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId)
                .userId(SecurityContextHolder.getUserId())
                .groupRoles(SecurityContextHolder.getGroupRoleMap())
                .build()).getData();
        if (permission == null || permission.getResourceAccessRole() != ResourceAccessRole.OWNER) {
            throw new ServiceException(SkillError.SKILL_PERMISSION_DENIED);
        }
    }
}
