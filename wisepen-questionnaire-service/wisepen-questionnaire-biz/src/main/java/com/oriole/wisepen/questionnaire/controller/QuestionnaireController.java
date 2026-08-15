package com.oriole.wisepen.questionnaire.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireDraftUpdateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireResourceRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmitRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireVersionRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireDefinitionResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionResponse;
import com.oriole.wisepen.questionnaire.exception.TableError;
import com.oriole.wisepen.questionnaire.service.QuestionnaireService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "问卷", description = "问卷设计、发布、填写和答卷查询")
@RestController
@RequestMapping("/questionnaire")
@RequiredArgsConstructor
public class QuestionnaireController {
    private final QuestionnaireService questionnaireService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "创建问卷",
            description = """
                    - 用途：为当前用户创建一个可设计和发布的问卷资源。
                    - 请求：title 是资源展示标题；description 可选；mountTargetTagId 可选，用于指定资源挂载路径标签。
                    - 约束：当前用户必须已登录；title 不能为空。
                    - 处理：调用资源服务注册 QUESTIONNAIRE 类型资源，创建问卷主档 version=0，并初始化 version=1 草稿表结构和问卷视图。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源注册失败 -> TableError.TABLE_REGISTER_RESOURCE_FAILED。
                    - 响应：返回问卷资源 ID。
                    """
    )
    @Log(title = "创建问卷", businessType = BusinessType.INSERT)
    @CheckRole
    @PostMapping("/createQuestionnaire")
    public R<String> createQuestionnaire(@Validated @RequestBody QuestionnaireCreateRequest request) {
        String resourceId = questionnaireService.createQuestionnaire(
                request, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap());
        return R.ok(resourceId);
    }

    @Operation(
            summary = "更新问卷草稿",
            description = """
                    - 用途：保存当前草稿版本的表结构、问卷视图和填写策略。
                    - 请求：resourceId 指定问卷资源；columns 是完整草稿字段列表；viewDefinition 是完整问卷视图定义；title 和 description 可选。
                    - 约束：当前用户必须拥有目标资源 EDIT 动作；只能更新当前草稿版本。
                    - 处理：覆盖保存 table.version + 1 对应的 DRAFT 表结构和同版本唯一问卷视图；不发布版本，不修改已发布版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；问卷不存在 -> TableError.TABLE_NOT_FOUND；当前版本不是草稿 -> TableError.TABLE_VERSION_STATUS_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "更新问卷草稿", businessType = BusinessType.UPDATE)
    @CheckRole
    @PostMapping("/updateDraftDefinition")
    public R<Void> updateDraftDefinition(@Validated @RequestBody QuestionnaireDraftUpdateRequest request) {
        assertHasAction(request.getResourceId(), null, ResourceAction.EDIT);
        questionnaireService.updateDraftDefinition(request);
        return R.ok();
    }

    @Operation(
            summary = "发布问卷版本",
            description = """
                    - 用途：将问卷当前草稿版本发布为可填写版本。
                    - 请求：resourceId 指定问卷资源。
                    - 约束：当前用户必须是资源所有者；目标草稿必须存在且状态为 DRAFT；字段定义、问卷视图和提交策略必须合法。
                    - 处理：将当前草稿置为 PUBLISHED，更新问卷主档当前发布版本，并复制发布内容生成下一版草稿。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；当前用户不是资源所有者 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；定义非法 -> TableError.TABLE_COLUMN_INVALID 或 TableError.QUESTIONNAIRE_VIEW_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "发布问卷版本", businessType = BusinessType.UPDATE)
    @CheckRole
    @PostMapping("/publishQuestionnaireVersion")
    public R<Void> publishQuestionnaireVersion(@Validated @RequestBody QuestionnaireResourceRequest request) {
        assertOwner(request.getResourceId());
        questionnaireService.publishQuestionnaireVersion(request.getResourceId());
        return R.ok();
    }

    @Operation(
            summary = "获取问卷",
            description = """
                    - 用途：获取用户可填写的问卷定义。
                    - 请求：resourceId 指定问卷资源；version 可选，未传时返回当前已发布版本。
                    - 约束：登录用户必须拥有目标资源 VIEW 动作；未登录访问时目标问卷必须允许 anonymousAllowed；目标版本必须是 PUBLISHED。
                    - 处理：读取目标版本表结构和同版本问卷视图；不返回草稿版本，不创建答卷。
                    - 失败：无 VIEW 权限或未开放匿名填写 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；版本不存在 -> TableError.TABLE_VERSION_NOT_FOUND；版本不是已发布 -> TableError.TABLE_VERSION_STATUS_INVALID。
                    - 响应：返回表结构、问卷视图和版本状态。
                    """
    )
    @PostMapping("/getQuestionnaire")
    public R<QuestionnaireDefinitionResponse> getQuestionnaire(@Validated @RequestBody QuestionnaireVersionRequest request) {
        QuestionnaireDefinitionResponse definition = questionnaireService.getQuestionnaire(request.getResourceId(), request.getVersion());
        if (SecurityContextHolder.getUserId() != null) {
            assertHasAction(request.getResourceId(), request.getVersion(), ResourceAction.VIEW);
            return R.ok(definition);
        }
        if (definition.getViewDefinition() == null || definition.getViewDefinition().getSubmissionPolicy() == null
                || !Boolean.TRUE.equals(definition.getViewDefinition().getSubmissionPolicy().getAnonymousAllowed())) {
            throw new ServiceException(TableError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
        return R.ok(definition);
    }

    @Operation(
            summary = "提交问卷",
            description = """
                    - 用途：提交或保存当前用户的问卷答卷。
                    - 请求：resourceId 指定问卷资源；version 可选，未传时使用当前已发布版本；status 为空时按 SUBMITTED；values 按 columnId 传值。
                    - 约束：登录用户必须拥有目标资源 VIEW 动作；未登录提交时目标问卷必须允许 anonymousAllowed；目标版本必须是 PUBLISHED；提交内容必须符合字段定义和提交策略。
                    - 处理：保存答卷原始 values、提交时 tableVersion 和当前用户 ID；匿名答卷 userId 为空，不能保存草稿或用于本人查询。
                    - 失败：无 VIEW 权限 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；提交策略不允许 -> TableError.SUBMISSION_NOT_ALLOWED；答卷内容非法 -> TableError.SUBMISSION_VALUE_INVALID。
                    - 响应：返回按提交版本投影后的答卷。
                    """
    )
    @Log(title = "提交问卷", businessType = BusinessType.INSERT)
    @PostMapping("/submitQuestionnaire")
    public R<QuestionnaireSubmissionResponse> submitQuestionnaire(@Validated @RequestBody QuestionnaireSubmitRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        if (userId != null) {
            assertHasAction(request.getResourceId(), request.getVersion(), ResourceAction.VIEW);
        }
        return R.ok(questionnaireService.submitQuestionnaire(request, userId));
    }

    @Operation(
            summary = "查询我的答卷",
            description = """
                    - 用途：当前用户查询自己在指定问卷下的答卷。
                    - 请求：resourceId 指定问卷资源；version 可选，作为答卷显示投影版本；page 和 size 控制分页。
                    - 约束：当前用户必须拥有目标资源 VIEW 动作；只返回当前登录用户自己的答卷。
                    - 处理：分页查询 userId 等于当前用户的答卷，默认按当前已发布版本投影显示。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 VIEW 权限 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；投影版本不存在 -> TableError.TABLE_VERSION_NOT_FOUND。
                    - 响应：返回分页答卷列表。
                    """
    )
    @CheckRole
    @PostMapping("/listMySubmissions")
    public R<PageR<QuestionnaireSubmissionResponse>> listMySubmissions(@Validated @RequestBody QuestionnaireSubmissionListRequest request) {
        assertHasAction(request.getResourceId(), request.getVersion(), ResourceAction.VIEW);
        return R.ok(questionnaireService.listMySubmissions(request, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "查询全部答卷",
            description = """
                    - 用途：问卷设计者分页查看指定问卷下的全部答卷结果。
                    - 请求：resourceId 指定问卷资源；version 可选，作为答卷显示投影版本；page 和 size 控制分页。
                    - 约束：当前用户必须拥有目标资源 EDIT 动作。
                    - 处理：分页查询该问卷全部答卷，默认按当前已发布版本投影显示；不会导出文件。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；投影版本不存在 -> TableError.TABLE_VERSION_NOT_FOUND。
                    - 响应：返回分页答卷列表。
                    """
    )
    @CheckRole
    @PostMapping("/listSubmissions")
    public R<PageR<QuestionnaireSubmissionResponse>> listSubmissions(@Validated @RequestBody QuestionnaireSubmissionListRequest request) {
        assertHasAction(request.getResourceId(), null, ResourceAction.EDIT);
        return R.ok(questionnaireService.listSubmissions(request));
    }

    @Operation(
            summary = "获取表结构",
            description = """
                    - 用途：获取问卷底层表结构和对应问卷视图。
                    - 请求：resourceId 指定问卷资源；version 可选，未传时返回当前已发布版本。
                    - 约束：读取已发布版本需要 VIEW 动作；读取草稿版本需要 EDIT 动作。
                    - 处理：返回目标版本字段结构和同版本问卷视图；不修改草稿或发布状态。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无所需权限 -> TableError.QUESTIONNAIRE_PERMISSION_DENIED；版本不存在 -> TableError.TABLE_VERSION_NOT_FOUND。
                    - 响应：返回表结构、问卷视图和版本状态。
                    """
    )
    @CheckRole
    @PostMapping("/getTable")
    public R<QuestionnaireDefinitionResponse> getTable(@Validated @RequestBody QuestionnaireVersionRequest request) {
        if (request.getVersion() == null) {
            ResourceCheckPermissionResDTO permission = getPermission(request.getResourceId(), null);
            if (hasAction(permission, ResourceAction.EDIT)) {
                return R.ok(questionnaireService.getDraftTable(request.getResourceId()));
            }
            if (!hasAction(permission, ResourceAction.VIEW)) {
                throw new ServiceException(TableError.QUESTIONNAIRE_PERMISSION_DENIED);
            }
            return R.ok(questionnaireService.getTable(request.getResourceId(), null, false));
        }
        boolean draftVersion = questionnaireService.isDraftVersion(request.getResourceId(), request.getVersion());
        assertHasAction(request.getResourceId(), request.getVersion(), draftVersion ? ResourceAction.EDIT : ResourceAction.VIEW);
        return R.ok(questionnaireService.getTable(request.getResourceId(), request.getVersion(), draftVersion));
    }

    private ResourceCheckPermissionResDTO getPermission(String resourceId, Integer targetVersion) {
        R<ResourceCheckPermissionResDTO> response = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId)
                .userId(SecurityContextHolder.getUserId())
                .groupRoles(SecurityContextHolder.getGroupRoleMap())
                .targetVersion(targetVersion)
                .build());
        return response == null ? null : response.getData();
    }

    private void assertHasAction(String resourceId, Integer targetVersion, ResourceAction action) {
        ResourceCheckPermissionResDTO permission = getPermission(resourceId, targetVersion);
        if (!hasAction(permission, action)) {
            throw new ServiceException(TableError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
    }

    private boolean hasAction(ResourceCheckPermissionResDTO permission, ResourceAction action) {
        return permission != null && permission.getAllowedActions() != null && permission.getAllowedActions().contains(action);
    }

    private void assertOwner(String resourceId) {
        ResourceCheckPermissionResDTO permission = getPermission(resourceId, null);
        if (permission == null || permission.getResourceAccessRole() != ResourceAccessRole.OWNER) {
            throw new ServiceException(TableError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
    }
}
