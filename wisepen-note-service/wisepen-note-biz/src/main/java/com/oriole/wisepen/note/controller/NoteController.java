package com.oriole.wisepen.note.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.note.api.domain.dto.req.NoteSnapshotSaveRequest;
import com.oriole.wisepen.note.api.domain.dto.req.NoteCreateRequest;
import com.oriole.wisepen.note.api.domain.dto.req.NoteForkRequest;
import com.oriole.wisepen.note.api.domain.dto.res.NoteInfoResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteVersionInfoResponse;
import com.oriole.wisepen.note.api.domain.enums.VersionType;
import com.oriole.wisepen.note.domain.entity.NoteInfoEntity;
import com.oriole.wisepen.note.service.INoteService;
import com.oriole.wisepen.note.service.INoteVersionService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceInfoGetReqDTO;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.note.exception.NoteError.CANNOT_SUPPORT_NOTE_RESOURCE_TYPE;
import static com.oriole.wisepen.note.exception.NoteError.NOTE_PERMISSION_DENIED;

@Slf4j
@Tag(name = "笔记", description = "笔记创建、详情查询、版本列表和版本回退")
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
@CheckRole
public class NoteController {

    private final INoteService noteService;
    private final INoteVersionService noteVersionService;
    private final RemoteResourceService remoteResourceService;
    private final RemoteUserService remoteUserService;

    @Operation(
            summary = "创建笔记",
            description = """
                    - 用途：为当前用户创建一份新的笔记体系资源，可创建普通协同笔记或 Draw.io 图。
                    - 请求：title 为资源标题；resourceType 可选，未传时按 NOTE 处理，允许 NOTE 或 DRAWIO；mountTargetTagId 可选，用于指定资源所属路径标签。
                    - 约束：当前用户必须已登录；title 必须是可用于展示的标题；resourceType 必须属于笔记服务支持的资源类型。
                    - 处理：调用资源服务注册对应类型资源，以当前用户作为所有者挂载到指定路径或个人根目录；随后创建笔记信息记录并将当前用户写入作者列表；不创建初始快照。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源类型不支持 -> NoteError.CANNOT_SUPPORT_NOTE_RESOURCE_TYPE；资源注册失败或笔记信息落库失败 -> NoteError.NOTE_REGISTER_RESOURCE_FAILED。
                    - 响应：返回新笔记的资源 ID。
                    """
    )
    @Log(title = "创建笔记", businessType = BusinessType.INSERT)
    @PostMapping("/addNote")
    public R<String> createNote(@Validated @RequestBody NoteCreateRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        String resourceId = noteService.createNote(request, userId, groupRoles);
        return R.ok(resourceId);
    }

    @Operation(
            summary = "复制笔记",
            description = """
                    - 用途：将当前用户拥有 FORK 动作的笔记体系资源复制为自己的新资源。
                    - 请求：resourceId 指定源笔记资源；forkedResourceVersion 可选，作为权限检查 targetVersion 并指定复制截止版本，未传时使用源笔记当前版本；forkedResourceName 指定新笔记资源名。
                    - 约束：当前用户必须拥有源资源 FORK 动作；Market 来源授权必须传当前上架 offerVersion；源笔记元信息必须存在。
                    - 处理：先调用资源服务实时校验 FORK 权限；服务层从源笔记元信息继承资源类型并注册新资源，复制截止版本前最近的 FULL 和后续 DELTA 快照，更新新笔记元信息版本为已复制版本中的最大值；DRAWIO 因只保存 FULL，通常只复制目标完整 XML 快照。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；源笔记不存在 -> NoteError.NOTE_NOT_FOUND；无 FORK 权限 -> NoteError.NOTE_PERMISSION_DENIED；资源注册失败 -> NoteError.NOTE_REGISTER_RESOURCE_FAILED；复制失败 -> NoteError.NOTE_FORK_FAILED。
                    - 响应：返回新笔记资源 ID。
                    """
    )
    @Log(title = "复制笔记", businessType = BusinessType.INSERT)
    @PostMapping("/forkNote")
    public R<String> forkNote(@Validated @RequestBody NoteForkRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId()).userId(userId).groupRoles(groupRoles).targetVersion(request.getForkedResourceVersion()).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.FORK)) {
            throw new ServiceException(NOTE_PERMISSION_DENIED);
        }
        return R.ok(noteService.forkNote(request, userId.toString()));
    }

    @Operation(
            summary = "获取笔记信息",
            description = """
                    - 用途：获取笔记资源详情、当前版本号和作者展示信息。
                    - 请求：resourceId 指定笔记资源；targetVersion 可选，仅用于 Market 版本限定权限裁决。
                    - 约束：当前用户必须已登录，且必须通过资源服务的资源详情权限校验；Market 来源查看必须传当前上架 offerVersion；目标笔记必须存在。
                    - 处理：通过资源服务获取资源详情和当前用户动作集合，读取笔记元信息中的当前版本号，并尽量调用用户服务补充作者展示信息；作者展示信息补充失败时不阻断主响应；不返回快照正文。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；资源无查看权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；笔记不存在 -> NoteError.NOTE_NOT_FOUND。
                    - 响应：返回资源信息、当前版本号和可用的作者展示信息。
                    """
    )
    @GetMapping("/getNoteInfo")
    public R<NoteInfoResponse> getNoteInfo(@RequestParam String resourceId,
                                           @RequestParam(value = "targetVersion", required = false) Integer targetVersion) {
        // 若无权限将抛出异常，此处无需重复鉴权
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap(), targetVersion
        )).getData();
        NoteInfoEntity noteInfo = noteService.getNoteInfo(resourceId);

        Map<Long, UserDisplayBase> authorsDisplay = null;
        try {
            authorsDisplay = remoteUserService.getUserDisplayInfo(noteInfo.getAuthors()).getData();
        } catch (Exception ignored){
        }

        NoteInfoResponse noteInfoResponse = NoteInfoResponse.builder()
                .resourceInfo(resourceInfo)
                .version(noteInfo.getVersion())
                .authorsDisplay(authorsDisplay)
                .build();
        return R.ok(noteInfoResponse);
    }

    @Operation(
            summary = "保存 Draw.io 笔记内容",
            description = """
                    - 用途：保存 Draw.io 图的完整 XML 快照，生成笔记体系资源的新版本。
                    - 请求：resourceId 指定 DRAWIO 资源；version 是本次要写入的快照版本号；data 是完整 Draw.io XML 的 Base64；plainText 可选，用于搜索摘要；type 会在入口被强制为 FULL。
                    - 约束：当前用户必须拥有目标资源 EDIT 动作；目标资源类型必须是 DRAWIO；version 应是该资源下尚未使用的新版本号。
                    - 处理：强制按 FULL 快照保存版本数据，更新笔记元信息中的当前版本和作者列表；plainText 非空时写入笔记内容摘要；不发送协同快照消息，不写 DELTA，不经过对象存储。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> NoteError.NOTE_PERMISSION_DENIED；笔记不存在 -> NoteError.NOTE_NOT_FOUND；资源类型不支持 -> NoteError.CANNOT_SUPPORT_NOTE_RESOURCE_TYPE。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "保存 Draw.io 笔记内容", businessType = BusinessType.UPDATE)
    @PostMapping("/saveDrawIOSnapshot")
    public R<Void> saveDrawIOSnapshot(@Validated @RequestBody NoteSnapshotSaveRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(
                ResourceCheckPermissionReqDTO.builder().resourceId(request.getResourceId()).userId(userId).groupRoles(groupRoles).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(NOTE_PERMISSION_DENIED);
        }
        // 必须是FULL
        request.setType(VersionType.FULL);
        noteVersionService.createVersion(request, List.of(userId), ResourceType.DRAWIO);
        return R.ok();
    }

    @Operation(
            summary = "获取最新 Draw.io 笔记快照",
            description = """
                    - 用途：供 Draw.io 拉取笔记最新完整快照。
                    - 请求：resourceId 指定笔记资源。
                    - 约束：当前用户必须拥有目标资源 VIEW 动作；目标资源类型必须是 DRAWIO；目标资源必须存在。
                    - 处理：读取最近一个 FULL 快照；如果没有 FULL 快照则返回空 fullSnapshot 和当前版本 0；不生成新版本。
                    - 失败：笔记不存在 -> NoteError.NOTE_NOT_FOUND；版本读取发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回资源 ID、最新版本号和快照。
                    """
    )
    @GetMapping("/getDrawIOLatestVersion")
    public R<NoteSnapshotResponse> getDrawIOLatestVersion(@RequestParam("resourceId") String resourceId) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(
                ResourceCheckPermissionReqDTO.builder().resourceId(resourceId).userId(userId).groupRoles(groupRoles).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(NOTE_PERMISSION_DENIED);
        }

        // 此接口限制仅 DrawIO 类型资源可用
        NoteInfoEntity noteInfoEntity = noteService.getNoteInfo(resourceId);
        if (noteInfoEntity.getResourceType() != ResourceType.DRAWIO) {
            throw new ServiceException(CANNOT_SUPPORT_NOTE_RESOURCE_TYPE);
        }

        return R.ok(noteVersionService.getLatestVersion(resourceId));
    }

    @Operation(
            summary = "分页查询笔记版本",
            description = """
                    - 用途：查询笔记的历史版本列表，供所有者查看版本轨迹。
                    - 请求：resourceId 指定笔记资源；page 和 size 控制分页。
                    - 约束：当前用户必须已登录，且必须是资源所有者。
                    - 处理：通过资源服务校验所有者身份后，按版本号倒序分页读取版本记录；不返回版本快照正文，不执行版本回退。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> NoteError.NOTE_PERMISSION_DENIED；权限计算发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回分页版本摘要列表和总数。
                    """
    )
    @GetMapping("/listNoteVersions")
    public R<PageR<NoteVersionInfoResponse>> listNoteVersions(
            @RequestParam String resourceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId).userId(SecurityContextHolder.getUserId()).groupRoles(SecurityContextHolder.getGroupRoleMap()).build()).getData();
        if (permission == null || permission.getResourceAccessRole() != ResourceAccessRole.OWNER) {
            throw new ServiceException(NOTE_PERMISSION_DENIED);
        }
        return R.ok(noteVersionService.listVersions(resourceId, page, size));
    }

    @Operation(
            summary = "回退笔记版本",
            description = """
                    - 用途：预留给笔记所有者将笔记回退到指定历史版本。
                    - 请求：resourceId 指定笔记资源；version 指定目标版本号。
                    - 约束：当前用户必须已登录，且必须是资源所有者。
                    - 处理：当前接口仅完成所有者权限校验，实际回退逻辑尚未实现；不会修改笔记内容、版本记录或协同状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> NoteError.NOTE_PERMISSION_DENIED；权限计算发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回空结果。
                    """
    )
    @PostMapping("/revertNote")
    public R<Void> revertToVersion(@RequestParam String resourceId, @RequestParam Long version) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId).userId(SecurityContextHolder.getUserId()).groupRoles(SecurityContextHolder.getGroupRoleMap()).build()).getData();
        if (permission == null || permission.getResourceAccessRole() != ResourceAccessRole.OWNER) {
            throw new ServiceException(NOTE_PERMISSION_DENIED);
        }
        // TODO: 待未来实现
        return R.ok();
    }
}
