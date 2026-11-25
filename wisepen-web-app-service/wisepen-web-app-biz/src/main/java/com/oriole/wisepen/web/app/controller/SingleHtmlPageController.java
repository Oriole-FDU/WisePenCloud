package com.oriole.wisepen.web.app.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceInfoGetReqDTO;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.web.app.domain.base.SingleHtmlPageVersionBase;
import com.oriole.wisepen.web.app.domain.dto.req.SingleHtmlPageCreateRequest;
import com.oriole.wisepen.web.app.domain.dto.req.SingleHtmlPageUploadInitRequest;
import com.oriole.wisepen.web.app.domain.dto.res.SingleHtmlPageInfoResponse;
import com.oriole.wisepen.web.app.domain.dto.res.SingleHtmlPageUploadInitResponse;
import com.oriole.wisepen.web.app.domain.dto.res.SingleHtmlPageVersionInfoResponse;
import com.oriole.wisepen.web.app.domain.entity.SingleHtmlPageEntity;
import com.oriole.wisepen.web.app.domain.entity.SingleHtmlPageVersionEntity;
import com.oriole.wisepen.web.app.enums.SingleHtmlPageStatus;
import com.oriole.wisepen.web.app.exception.WebAppError;
import com.oriole.wisepen.web.app.service.ISingleHtmlPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Single HTML Page", description = "Single HTML Page 信息、版本和内容访问")
@RestController
@RequestMapping("/singleHtmlPage")
@RequiredArgsConstructor
@Validated
@CheckRole
public class SingleHtmlPageController {

    private static final String CONTENT_SECURITY_POLICY = "sandbox allow-scripts; default-src 'none'; "
            + "script-src 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src data: blob:; "
            + "connect-src 'none'; form-action 'none'; base-uri 'none'; frame-ancestors 'self'";

    private final ISingleHtmlPageService singleHtmlPageService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "创建 Single HTML Page",
            description = """
                    - 用途：为当前用户创建一个尚无内容版本的 Single HTML Page 资源。
                    - 请求：title 为页面资源标题；mountTargetTagId 可选，用于指定初始挂载路径。
                    - 约束：当前用户必须已登录；显式路径必须符合资源服务的个人或小组挂载权限。
                    - 处理：注册 SINGLE_HTML_PAGE 资源并创建 currentVersion=0 的页面主档；mountTargetTagId 为空时由资源服务挂载到个人 .Page 系统目录；不初始化上传，也不创建可访问版本。
                    - 失败：资源注册失败 -> WebAppError.SINGLE_HTML_PAGE_REGISTER_RESOURCE_FAILED。
                    - 响应：返回新页面资源 ID。
                    """
    )
    @Log(title = "创建 Single HTML Page", businessType = BusinessType.INSERT)
    @PostMapping("/createSingleHtmlPage")
    public R<String> createSingleHtmlPage(@Valid @RequestBody SingleHtmlPageCreateRequest request) {
        return R.ok(singleHtmlPageService.createSingleHtmlPage(
                request, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap()));
    }

    @Operation(
            summary = "初始化 Single HTML Page 上传",
            description = """
                    - 用途：为已有页面创建下一个待上传版本，并申请对象存储直传凭证。
                    - 请求：resourceId 指定页面资源；filename 必须是 html 或 htm；md5 和 expectedSize 描述待上传文件。
                    - 约束：当前用户必须拥有资源 EDIT 动作；文件大小不能超过配置上限；同一版本号不能存在其他上传任务。
                    - 处理：固定使用 PRIVATE_WEB_APP_ASSET 场景申请预签名 PUT URL，保存 UPLOADING 版本；命中秒传时直接校验内容并推进为 READY。
                    - 失败：没有 EDIT 动作 -> WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED；文件类型不合法 -> WebAppError.SINGLE_HTML_PAGE_FILE_TYPE_INVALID；文件过大 -> WebAppError.SINGLE_HTML_PAGE_CONTENT_TOO_LARGE；版本冲突 -> WebAppError.SINGLE_HTML_PAGE_VERSION_CONFLICT；申请上传凭证失败 -> WebAppError.SINGLE_HTML_PAGE_UPLOAD_INIT_FAILED。
                    - 响应：返回 singleHtmlPageId、候选版本号、objectKey、PUT URL、回调 Header 和是否秒传。
                    """
    )
    @Log(title = "初始化 Single HTML Page 上传", businessType = BusinessType.INSERT)
    @PostMapping("/uploadSingleHtmlPage")
    public R<SingleHtmlPageUploadInitResponse> uploadSingleHtmlPage(
            @Valid @RequestBody SingleHtmlPageUploadInitRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        if (request.getResourceId() != null) { // 更新现有版本
            ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                    .resourceId(request.getResourceId()).userId(userId).groupRoles(groupRoles).build()).getData();
            if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
                throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED);
            }
        }
        return R.ok(singleHtmlPageService.initUploadSingleHtmlPage(request, userId, groupRoles));
    }

    @Operation(
            summary = "查询未就绪 Single HTML Page 版本",
            description = """
                    - 用途：查询当前用户发起但尚未就绪或处理失败的页面版本上传任务。
                    - 请求：无显式参数，上传者来自当前登录上下文。
                    - 约束：当前用户必须已登录。
                    - 处理：查询当前用户的 UPLOADING、UPLOADED 和 FAILED 版本摘要；不刷新 OSS 状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回未就绪版本摘要列表。
                    """
    )
    @GetMapping("/listPendingSingleHtmlPageVersions")
    public R<List<SingleHtmlPageVersionBase>> listPendingSingleHtmlPageVersions() {
        return R.ok(singleHtmlPageService.listPending(SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "刷新 Single HTML Page 上传状态",
            description = """
                    - 用途：上传回调丢失时主动查询对象存储状态并推进页面版本。
                    - 请求：singleHtmlPageId 指定上传任务。
                    - 约束：当前用户必须是该版本上传者。
                    - 处理：查询 StorageRecord；文件可用时校验 HTML 内容并调用 finalizeToReady 推进为 READY。
                    - 失败：版本不存在 -> WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND；当前用户不是上传者 -> WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED；HTML 内容非法 -> WebAppError.SINGLE_HTML_PAGE_CONTENT_INVALID。
                    - 响应：返回刷新后的版本状态。
                    """
    )
    @Log(title = "刷新 Single HTML Page 上传状态", businessType = BusinessType.UPDATE)
    @PostMapping("/syncSingleHtmlPageStatus")
    public R<SingleHtmlPageStatus> syncSingleHtmlPageStatus(@RequestParam String singleHtmlPageId) {
        singleHtmlPageService.assertVersionUploader(singleHtmlPageId, SecurityContextHolder.getUserId());
        return R.ok(singleHtmlPageService.refreshSingleHtmlPageStatus(singleHtmlPageId));
    }

    @Operation(
            summary = "取消 Single HTML Page 上传",
            description = """
                    - 用途：取消当前用户发起且尚未就绪的页面版本上传任务。
                    - 请求：singleHtmlPageId 指定上传任务。
                    - 约束：当前用户必须是该版本上传者；READY 版本不能取消。
                    - 处理：标记关联对象存储文件为删除并删除版本任务；不删除页面资源主档。
                    - 失败：版本不存在 -> WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND；当前用户不是上传者 -> WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED；版本已经 READY -> WebAppError.SINGLE_HTML_PAGE_PROCESS_STATE_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "取消 Single HTML Page 上传", businessType = BusinessType.DELETE)
    @PostMapping("/cancelSingleHtmlPageUpload")
    public R<Void> cancelSingleHtmlPageUpload(@RequestParam String singleHtmlPageId) {
        singleHtmlPageService.assertVersionUploader(singleHtmlPageId, SecurityContextHolder.getUserId());
        singleHtmlPageService.deletedSingleHtmlPageVersion(singleHtmlPageId);
        return R.ok();
    }

    @Operation(
            summary = "获取 Single HTML Page 信息",
            description = """
                    - 用途：获取页面资源详情、当前版本和指定版本摘要。
                    - 请求：resourceId 指定页面资源；targetVersion 可选，未传时读取当前版本。
                    - 约束：当前用户必须已登录并拥有资源 VIEW 动作；指定版本必须存在。
                    - 处理：通过资源服务校验目标版本访问权限，组合资源详情、页面当前版本及目标版本摘要；不返回 HTML 正文。
                    - 失败：资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；没有 VIEW 动作 -> ResourceError.RESOURCE_PERMISSION_DENIED；页面不存在 -> WebAppError.SINGLE_HTML_PAGE_NOT_FOUND；版本不存在 -> WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND。
                    - 响应：返回资源详情、当前版本号和目标版本摘要。
                    """
    )
    @PostMapping("/getSingleHtmlPageInfo")
    public R<SingleHtmlPageInfoResponse> getSingleHtmlPageInfo(
            @RequestParam String resourceId,
            @RequestParam(value = "targetVersion", required = false) Integer targetVersion) {
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap(), targetVersion
        )).getData();
        if (resourceInfo == null || resourceInfo.getResourceType() != ResourceType.SINGLE_HTML_PAGE) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_NOT_FOUND);
        }
        SingleHtmlPageEntity singleHtmlPageInfo = singleHtmlPageService.getSingleHtmlPageInfo(resourceId);

        Integer effectiveVersion = targetVersion != null ? targetVersion : singleHtmlPageInfo.getCurrentVersion();
        SingleHtmlPageVersionInfoResponse singleHtmlPageVersionInfo = null;
        // 版本号不为 0 时，额外查询
        if (!Integer.valueOf(0).equals(effectiveVersion)) {
            SingleHtmlPageVersionEntity singleHtmlPageVersionEntity = singleHtmlPageService.getSingleHtmlPageVersion(resourceId, effectiveVersion);
            singleHtmlPageVersionInfo = BeanUtil.copyProperties(singleHtmlPageVersionEntity, SingleHtmlPageVersionInfoResponse.class);
        }

        return R.ok(SingleHtmlPageInfoResponse.builder()
                .resourceInfo(resourceInfo)
                .currentVersion(singleHtmlPageInfo.getCurrentVersion())
                .versionInfo(singleHtmlPageVersionInfo)
                .build());
    }

    @Operation(
            summary = "获取 Single HTML Page 内容",
            description = """
                    - 用途：读取页面当前版本或指定历史版本的完整 HTML，供受控页面容器加载。
                    - 请求：resourceId 指定页面资源；targetVersion 可选，未传时读取当前版本。
                    - 约束：当前用户必须已登录并拥有目标版本 VIEW 动作；指定版本必须存在。
                    - 处理：通过资源服务校验 VIEW 权限，从对象存储读取并校验内容摘要后输出 HTML 正文；输出前将 WisePen UI 样式外链内联进正文，同时附加 sandbox CSP、禁止缓存和禁止 MIME 嗅探等安全响应头；不修改页面或版本。
                    - 失败：没有 VIEW 动作 -> WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED；页面不存在 -> WebAppError.SINGLE_HTML_PAGE_NOT_FOUND；版本不存在 -> WebAppError.SINGLE_HTML_PAGE_VERSION_NOT_FOUND；对象存储内容读取或摘要校验失败 -> WebAppError.SINGLE_HTML_PAGE_CONTENT_READ_FAILED。
                    - 响应：返回 text/html;charset=UTF-8 的页面正文。
                    """
    )
    @GetMapping(value = "/getSingleHtmlPageContent", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSingleHtmlPageContent(
            @RequestParam String resourceId,
            @RequestParam(value = "targetVersion", required = false) Integer targetVersion) {
        assertReadable(resourceId, targetVersion);
        String htmlContent = singleHtmlPageService.getSingleHtmlPageContent(resourceId, targetVersion);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(htmlContent);
    }

    @Operation(
            summary = "分页查询 Single HTML Page 版本",
            description = """
                    - 用途：查询页面的完整历史版本轨迹。
                    - 请求：resourceId 指定页面资源；page 和 size 控制分页。
                    - 约束：当前用户必须已登录并拥有资源 VIEW 动作；page 从 1 开始，size 最大为 100。
                    - 处理：通过资源服务校验 VIEW 权限后按版本号倒序分页读取版本摘要；不返回 HTML 正文。
                    - 失败：没有 VIEW 动作 -> WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED；页面不存在 -> WebAppError.SINGLE_HTML_PAGE_NOT_FOUND。
                    - 响应：返回版本摘要分页列表和总数。
                    """
    )
    @GetMapping("/listSingleHtmlPageVersions")
    public R<PageR<SingleHtmlPageVersionInfoResponse>> listSingleHtmlPageVersions(
            @RequestParam String resourceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        assertReadable(resourceId, null);
        return R.ok(singleHtmlPageService.listVersions(resourceId, page, size));
    }

    private void assertReadable(String resourceId, Integer targetVersion) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(
                ResourceCheckPermissionReqDTO.builder()
                        .resourceId(resourceId)
                        .userId(SecurityContextHolder.getUserId())
                        .groupRoles(SecurityContextHolder.getGroupRoleMap())
                        .targetVersion(targetVersion)
                        .build()
        ).getData();
        if (permission == null || permission.getAllowedActions() == null
                || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(WebAppError.SINGLE_HTML_PAGE_PERMISSION_DENIED);
        }
    }
}
