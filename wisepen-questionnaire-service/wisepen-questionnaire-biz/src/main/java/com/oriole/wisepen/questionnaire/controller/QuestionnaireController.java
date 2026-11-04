package com.oriole.wisepen.questionnaire.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireDraftUpdateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireForkRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireInfoResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireVersionInfoResponse;
import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import com.oriole.wisepen.questionnaire.service.IQuestionnaireService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceInfoGetReqDTO;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "问卷", description = "问卷创建、复制、设计、发布和定义查询")
@RestController
@RequestMapping("/questionnaire")
@RequiredArgsConstructor
@Validated
public class QuestionnaireController {
    private final IQuestionnaireService questionnaireService;
    private final RemoteResourceService remoteResourceService;
    private final RemoteUserService remoteUserService;

    @Operation(
            summary = "创建问卷",
            description = """
                    - 用途：创建归当前用户所有、可继续设计和发布的问卷。
                    - 请求：title 是资源标题；description 和 mountTargetTagId 可选，后者指定资源挂载标签。
                    - 约束：当前用户必须已登录，title 不能为空。
                    - 处理：注册 QUESTIONNAIRE 资源，将 description 写入 version=1 草稿；记录创建者为作者，初始化 columns 和 pages 均为空列表的草稿及默认提交策略。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源注册失败 -> QuestionnaireError.QUESTIONNAIRE_REGISTER_RESOURCE_FAILED；本地数据初始化失败 -> QuestionnaireError.QUESTIONNAIRE_INITIALIZATION_FAILED。
                    - 响应：返回问卷资源 ID。
                    """
    )
    @Log(title = "创建问卷", businessType = BusinessType.INSERT)
    @CheckRole
    @PostMapping("/createQuestionnaire")
    public R<String> createQuestionnaire(@Validated @RequestBody QuestionnaireCreateRequest request) {
        String resourceId = questionnaireService.createQuestionnaire(
                request, SecurityContextHolder.getUserId().toString(), SecurityContextHolder.getGroupRoleMap());
        return R.ok(resourceId);
    }

    @Operation(
            summary = "获取问卷草稿",
            description = """
                    - 用途：为问卷编辑页提供当前可编辑草稿及资源信息。
                    - 请求：resourceId 指定问卷资源。
                    - 约束：当前用户必须已登录并拥有目标资源 EDIT 动作；草稿版本固定为当前发布版本加 1。
                    - 处理：获取资源详情并校验 EDIT 权限，读取主档作者及当前草稿，批量补充作者展示信息；不修改问卷数据。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；无 EDIT 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；
                           问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；草稿不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND。
                    - 响应：resourceInfo 返回资源详情，questionnaireVersionInfo 返回草稿版本号、描述及完整问卷定义，authorsDisplay 返回作者 ID 到展示信息的映射；作者信息查询失败时为 null。
                    """
    )
    @CheckRole
    @GetMapping("/getQuestionnaireDraft")
    public R<QuestionnaireInfoResponse> getQuestionnaireDraft(@RequestParam String resourceId) {
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap(), null)).getData();
        if (resourceInfo == null || resourceInfo.getCurrentActions() == null || !resourceInfo.getCurrentActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }

        TableEntity questionnaireInfo = questionnaireService.getQuestionnaireInfo(resourceId);
        QuestionnaireVersionInfoResponse questionnaireVersionInfo = questionnaireService.getQuestionnaireDraft(resourceId, questionnaireInfo.getVersion() + 1);

        Map<Long, UserDisplayBase> authorsDisplay = null;
        try {
            authorsDisplay = remoteUserService.getUserDisplayInfo(questionnaireInfo.getAuthors()).getData();
        } catch (Exception ignored) {
        }

        QuestionnaireInfoResponse response = QuestionnaireInfoResponse.builder()
                .resourceInfo(resourceInfo)
                .questionnaireVersionInfo(questionnaireVersionInfo)
                .authorsDisplay(authorsDisplay)
                .build();
        return R.ok(response);
    }

    @Operation(
            summary = "更新问卷草稿",
            description = """
                    - 用途：保存完整问卷设计，形成待发布的当前草稿。
                    - 请求：resourceId 和 version 标识当前草稿；columns、pages、completionMessage、submissionPolicy 直接放在请求顶层。pages 可选，通过 columnId 引用列，列表顺序即展示顺序；maxSubmissionsPerUser 为 null 表示不限次数。
                    - 约束：当前用户必须拥有 EDIT 动作，version 必须匹配当前草稿；columnId 唯一，页面只能引用已有列且不能重复编排，题型定义和默认值必须合法，开始时间不得晚于结束时间。
                    - 处理：校验列定义、页面引用及提交时间范围后全量替换草稿内容；不修改已发布版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；草稿版本不匹配 -> QuestionnaireError.TABLE_VERSION_CONFLICT；
                           草稿不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；列 ID 或页面引用重复 -> QuestionnaireError.TABLE_COLUMN_DUPLICATED；列定义、默认值非法或页面引用的列不存在 -> QuestionnaireError.TABLE_COLUMN_INVALID；提交时间范围非法 -> QuestionnaireError.SUBMISSION_POLICY_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "更新问卷草稿", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @CheckRole
    @PostMapping("/updateQuestionnaireDraft")
    public R<Void> updateQuestionnaireDraft(@Validated @RequestBody QuestionnaireDraftUpdateRequest request) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId()).userId(SecurityContextHolder.getUserId()).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }

        questionnaireService.updateQuestionnaireDraft(request);
        return R.ok();
    }

    @Operation(
            summary = "发布问卷版本",
            description = """
                    - 用途：将当前草稿发布为新的可查看、可填写版本。
                    - 请求：resourceId 指定问卷资源。
                    - 约束：当前用户必须已登录并拥有目标资源 EDIT 动作；当前草稿必须存在。
                    - 处理：将当前草稿标记为 PUBLISHED，更新问卷主档的当前发布版本，并复制其完整定义生成下一版 DRAFT；不修改既有历史版本。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；当前草稿不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；发布写入失败 -> QuestionnaireError.QUESTIONNAIRE_PUBLISH_FAILED。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "发布问卷版本", businessType = BusinessType.UPDATE)
    @CheckRole
    @PostMapping("/publishQuestionnaireVersion")
    public R<Void> publishQuestionnaireVersion(@RequestParam String resourceId) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId).userId(SecurityContextHolder.getUserId()).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }

        questionnaireService.publishQuestionnaireVersion(resourceId);
        return R.ok();
    }

    @Operation(
            summary = "获取已发布问卷",
            description = """
                    - 用途：为问卷查看和体验场景提供资源详情及已发布内容。
                    - 请求：resourceId 指定问卷资源；version 可选，未传时返回当前最新已发布版本。
                    - 约束：当前用户必须已登录并拥有目标版本的 VIEW 动作；目标版本必须为 PUBLISHED，不校验填写时间和提交次数。
                    - 处理：获取资源详情并校验 VIEW 权限，读取主档作者及指定或当前发布版本，批量补充作者展示信息；不返回草稿，不创建答卷。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；无 VIEW 权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；
                                    问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；目标版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；目标版本未发布 -> QuestionnaireError.TABLE_VERSION_STATUS_INVALID。
                    - 响应：resourceInfo 返回资源详情，questionnaireVersionInfo 返回实际读取的版本号、描述及完整问卷定义，authorsDisplay 返回主档作者的展示信息；作者信息查询失败时为 null。
                    """
    )
    @CheckRole
    @GetMapping("/getQuestionnaire")
    public R<QuestionnaireInfoResponse> getQuestionnaire(@RequestParam String resourceId, @RequestParam(required = false) Integer version) {
        // 若无权限将抛出异常，此处无需重复鉴权
        ResourceItemResponse resourceInfo = remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap(), version)).getData();

        TableEntity questionnaireInfo = questionnaireService.getQuestionnaireInfo(resourceId);
        Integer effectiveVersion = version != null ? version : questionnaireInfo.getVersion();
        QuestionnaireVersionInfoResponse questionnaireVersionInfo = questionnaireService.getQuestionnaire(resourceId, effectiveVersion);

        Map<Long, UserDisplayBase> authorsDisplay = null;
        try {
            authorsDisplay = remoteUserService.getUserDisplayInfo(questionnaireInfo.getAuthors()).getData();
        } catch (Exception ignored) {
        }

        QuestionnaireInfoResponse response = QuestionnaireInfoResponse.builder()
                .resourceInfo(resourceInfo)
                .questionnaireVersionInfo(questionnaireVersionInfo)
                .authorsDisplay(authorsDisplay)
                .build();
        return R.ok(response);
    }

    @Operation(
            summary = "复制问卷",
            description = """
                    - 用途：基于已有问卷的已发布版本创建一份归当前用户所有的新问卷。
                    - 请求：resourceId 指定源问卷；forkedResourceVersion 可选，未传时使用源问卷当前发布版本；forkedResourceName 是新资源名称。
                    - 约束：当前用户必须已登录并拥有源资源 FORK 动作；Market 来源必须指定当前上架版本；源版本必须存在且为 PUBLISHED。
                    - 处理：校验源版本 FORK 权限并注册新资源，将源已发布版本的完整定义复制为 version=1 的 PUBLISHED，同时初始化内容相同的 version=2 DRAFT；不复制源作者、其他版本和答卷。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 FORK 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；源问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；
                           源版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；源版本未发布 -> QuestionnaireError.TABLE_VERSION_STATUS_INVALID；新资源注册失败 -> QuestionnaireError.QUESTIONNAIRE_REGISTER_RESOURCE_FAILED；本地复制失败 -> QuestionnaireError.QUESTIONNAIRE_FORK_FAILED。
                    - 响应：返回新问卷资源 ID。
                    """
    )
    @Log(title = "复制问卷", businessType = BusinessType.INSERT)
    @CheckRole
    @PostMapping("/forkQuestionnaire")
    public R<String> forkQuestionnaire(@Validated @RequestBody QuestionnaireForkRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId()).userId(userId).groupRoles(groupRoles).targetVersion(request.getForkedResourceVersion()).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.FORK)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
        return R.ok(questionnaireService.forkQuestionnaire(request, userId.toString()));
    }
}
