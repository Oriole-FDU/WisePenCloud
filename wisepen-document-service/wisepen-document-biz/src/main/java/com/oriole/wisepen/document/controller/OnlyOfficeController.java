package com.oriole.wisepen.document.controller;

import com.onlyoffice.model.documenteditor.Callback;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.document.api.domain.dto.res.OnlyOfficeEditorConfigResponse;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.exception.DocumentError;
import com.oriole.wisepen.document.service.IOnlyOfficeEditService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.document.api.constant.DocumentConstants.ONLY_OFFICE_CALLBACK_PATH;

@Tag(name = "ONLYOFFICE 文档编辑", description = "ONLYOFFICE 编辑器配置与保存回调")
@RestController
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final IOnlyOfficeEditService onlyOfficeEditService;
    private final DocumentProperties documentProperties;
    private final RemoteResourceService remoteResourceService;
    private final RemoteUserService remoteUserService;

    @CheckRole
    @Operation(
            summary = "获取 ONLYOFFICE 编辑器配置",
            description = """
                    - 用途：为未来前端嵌入 ONLYOFFICE 编辑器提供完整配置。
                    - 请求：resourceId 指定文档资源。
                    - 约束：当前用户必须拥有 EDIT 权限；仅支持最新 READY Office 文档；PDF 首版不支持编辑。
                    - 处理：复用同一资源的活跃编辑会话，生成 document/editorConfig/JWT token。
                    - 失败：文档资源不存在 -> DocumentError.DOCUMENT_NOT_FOUND；文档预览未就绪 -> DocumentError.DOCUMENT_PREVIEW_NOT_READY；非 Office 可编辑类型 -> DocumentError.DOCUMENT_EDIT_NOT_SUPPORTED；当前用户无编辑权限 -> DocumentError.DOCUMENT_PERMISSION_DENIED。
                    - 响应：返回 Document Server 前端地址、sessionId 与 editor config。
                    """
    )
    @GetMapping("/document/onlyoffice/editorConfig")
    public R<OnlyOfficeEditorConfigResponse> getEditorConfig(@RequestParam String resourceId) {
        Long authorUserId = SecurityContextHolder.getUserId();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId)
                .userId(authorUserId)
                .groupRoles(SecurityContextHolder.getGroupRoleMap())
                .build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(DocumentError.DOCUMENT_PERMISSION_DENIED);
        }

        UserDisplayBase authorDisplay = null;
        try {
            Map<Long, UserDisplayBase> authorsDisplays = remoteUserService.getUserDisplayInfo(List.of(authorUserId)).getData();
            authorDisplay = authorsDisplays.get(authorUserId);
        } catch (Exception ignored){
        }

        return R.ok(onlyOfficeEditService.getEditorConfig(resourceId, authorUserId, permission, authorDisplay));
    }

    @Operation(
            summary = "接收 ONLYOFFICE 保存回调",
            description = """
                    - 用途：接收 ONLYOFFICE Document Server 的保存回调。
                    - 请求：sessionId 指定编辑会话；请求体是 ONLYOFFICE 回调载荷；Authorization 头或请求体 token 用于 JWT 校验。
                    - 约束：该接口不依赖登录态，必须通过 ONLYOFFICE JWT 和 session 校验。
                    - 处理：status=6 保存草稿不发布；status=2 创建新文档版本并发布解析任务；status=4 关闭会话。
                    - 失败：编辑会话不存在或过期 -> DocumentError.DOCUMENT_EDIT_SESSION_NOT_FOUND；回调签名或 document key 校验失败 -> DocumentError.DOCUMENT_EDIT_CALLBACK_INVALID；保存编辑结果失败 -> DocumentError.DOCUMENT_EDIT_SAVE_FAILED。
                    - 响应：按 ONLYOFFICE 要求返回 error=0。
                    """
    )
    @PostMapping(ONLY_OFFICE_CALLBACK_PATH + "{sessionId}")
    public Map<String, Integer> callback(@PathVariable String sessionId, @RequestBody Callback request, HttpServletRequest servletRequest) {
        return onlyOfficeEditService.handleCallback(sessionId, request);
    }
}
