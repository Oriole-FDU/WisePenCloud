package com.oriole.wisepen.questionnaire.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.questionnaire.api.domain.base.QuestionnaireDefinitionBase;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionSaveRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionUpdateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionDetailResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionItemResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireDefinitionResponse;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import com.oriole.wisepen.questionnaire.service.IQuestionnaireSubmissionService;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionResDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceInfoGetReqDTO;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
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

import java.util.List;

@Tag(name = "问卷答卷", description = "问卷填写、提交与答卷查询")
@RestController
@RequestMapping("/questionnaire")
@RequiredArgsConstructor
@Validated
public class QuestionnaireSubmissionController {

    private final IQuestionnaireSubmissionService questionnaireSubmissionService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "获取可填写问卷",
            description = """
                    - 用途：填写者进入填写页前获取当前问卷内容并确认是否可以新建答卷。
                    - 请求：resourceId 指定问卷资源。
                    - 约束：当前用户必须已登录并拥有资源 VIEW 动作；问卷处于填写时间内，且当前用户未达到提交次数上限。
                    - 处理：获取当前发布的问卷定义，校验开始时间、结束时间及当前用户提交次数；不创建或修改答卷。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；无 VIEW 权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；
                           当前发布版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；填写尚未开始 -> QuestionnaireError.SUBMISSION_NOT_STARTED；填写已结束 -> QuestionnaireError.SUBMISSION_ENDED；已达提交次数上限 -> QuestionnaireError.SUBMISSION_LIMIT_REACHED。
                    - 响应：返回完整问卷定义。
                    """
    )
    @CheckRole
    @GetMapping("/getQuestionnaireForFilling")
    public R<QuestionnaireDefinitionResponse> getQuestionnaireForFilling(@RequestParam String resourceId) {
        Long userId = SecurityContextHolder.getUserId();
        // 若无权限将抛出异常，此处无需重复鉴权
        remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, userId, SecurityContextHolder.getGroupRoleMap(), null));

        QuestionnaireDefinitionBase questionnaireInfo = questionnaireSubmissionService.getQuestionnaireForFilling(resourceId, userId);
        QuestionnaireDefinitionResponse response = BeanUtil.copyProperties(questionnaireInfo, QuestionnaireDefinitionResponse.class);
        return R.ok(response);
    }

    @Operation(
            summary = "提交答卷",
            description = """
                    - 用途：提交当前问卷的完整答案并创建一份正式答卷。
                    - 请求：resourceId 指定问卷资源；values 以 columnId 为键携带全部答案，填写者无需传入匿名标记。
                    - 约束：当前用户必须已登录并拥有资源 VIEW 动作；当前发布版本必须存在且未超过结束时间，所有必填题和答案格式必须合法；开始时间及提交次数由获取可填写问卷接口校验。
                    - 处理：按当前发布版本过滤并校验答案后创建答卷，保存真实 userId 供归属及次数判断；匿名仅影响后续身份展示，不保存服务端草稿。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 VIEW 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；当前发布版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；填写已结束 -> QuestionnaireError.SUBMISSION_ENDED；答案或必填项非法 -> QuestionnaireError.SUBMISSION_VALUE_INVALID。
                    - 响应：返回答卷 ID。
                    """
    )
    @CheckRole
    @Log(title = "提交答卷", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping("/submitQuestionnaire")
    public R<String> submitQuestionnaire(@Validated @RequestBody QuestionnaireSubmissionSaveRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId()).userId(userId).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }

        return R.ok(questionnaireSubmissionService.submitQuestionnaire(request.getResourceId(), request.getValues(), userId));
    }

    @Operation(
            summary = "查询我的答卷",
            description = """
                    - 用途：为填写者提供自己在指定问卷下的答卷列表。
                    - 请求：resourceId 指定问卷资源。
                    - 约束：当前用户必须已登录并拥有目标资源 VIEW 动作。
                    - 处理：按提交时间倒序查询当前用户的全部答卷；不返回问卷定义和答案内容。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 VIEW 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED。
                    - 响应：返回答卷 ID、提交时间和更新时间列表。
                    """
    )
    @CheckRole
    @GetMapping("/listMySubmissions")
    public R<List<QuestionnaireSubmissionItemResponse>> listMySubmissions(@RequestParam String resourceId) {
        Long userId = SecurityContextHolder.getUserId();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId).userId(userId).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
        return R.ok(questionnaireSubmissionService.listMySubmissions(resourceId, userId));
    }

    @Operation(
            summary = "获取我的答卷详情",
            description = """
                    - 用途：填写者查看自己某份答卷的当前问卷内容和答案。
                    - 请求：resourceId 指定问卷资源；submissionId 指定目标答卷。
                    - 约束：当前用户必须已登录并拥有目标资源 VIEW 动作；目标答卷必须属于当前用户；查询不受填写时间限制。
                    - 处理：读取当前发布的问卷定义，将已存答案按当前题目投影后返回；不修改答卷及其历史答案。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；无 VIEW 权限 -> ResourceError.RESOURCE_PERMISSION_DENIED；
                           答卷不存在或不属于当前用户 -> QuestionnaireError.SUBMISSION_NOT_FOUND；问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；当前发布版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND。
                    - 响应：返回完整问卷定义、答卷 ID 及映射后的答案。
                    """
    )
    @CheckRole
    @GetMapping("/getSubmission")
    public R<QuestionnaireSubmissionDetailResponse> getSubmission(@RequestParam String resourceId, @RequestParam String submissionId) {
        Long userId = SecurityContextHolder.getUserId();
        // 若无权限将抛出异常，此处无需重复鉴权
        remoteResourceService.getResourceInfo(new ResourceInfoGetReqDTO(
                resourceId, userId, SecurityContextHolder.getGroupRoleMap(), null));

        QuestionnaireSubmissionDetailResponse response = questionnaireSubmissionService.getSubmission(resourceId, submissionId, userId);
        return R.ok(response);
    }

    @Operation(
            summary = "更新已提交答卷",
            description = """
                    - 用途：填写者局部修改自己已经提交的答卷。
                    - 请求：resourceId 和 submissionId 指定目标答卷；values 仅携带本次需要修改的 columnId 与答案。
                    - 约束：当前用户必须已登录并拥有资源 VIEW 动作；答卷必须属于当前用户，当前发布策略允许提交后编辑且未超过结束时间。
                    - 处理：按当前题型校验并使用 MongoDB $set 新增或覆盖有效答案；未传答案及历史题目答案保持不变，请求中当前不存在的题目被忽略。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 VIEW 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；答卷不存在或不属于当前用户 -> QuestionnaireError.SUBMISSION_NOT_FOUND；
                           问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；当前发布版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；当前版本未发布 -> QuestionnaireError.TABLE_VERSION_STATUS_INVALID；
                           填写已结束 -> QuestionnaireError.SUBMISSION_ENDED；不允许提交后编辑 -> QuestionnaireError.SUBMISSION_UPDATE_NOT_ALLOWED；答案非法 -> QuestionnaireError.SUBMISSION_VALUE_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckRole
    @Log(title = "更新已提交答卷", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PostMapping("/updateSubmission")
    public R<Void> updateSubmission(@Validated @RequestBody QuestionnaireSubmissionUpdateRequest request) {
        Long userId = SecurityContextHolder.getUserId();
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId()).userId(userId).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.VIEW)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }

        questionnaireSubmissionService.updateSubmission(request.getResourceId(), request.getSubmissionId(), request.getValues(), userId);
        return R.ok();
    }

    @Operation(
            summary = "分页查询全部答卷",
            description = """
                    - 用途：问卷发起者以多维表格形式分页浏览指定问卷的全部答卷。
                    - 请求：resourceId 指定问卷资源；page 和 size 控制分页；userIds 可按一组填写者筛选；filters 可省略，筛选组按 AND 或 OR 递归组合题目条件，EQUALS 用于布尔题，ANY_OF 和 ALL_OF 用于选择题，RANGE 用于数字题；sorts 按列表顺序设置数字题或时间题排序。
                    - 约束：当前用户必须已登录并拥有目标资源 EDIT 动作；匿名问卷不允许按 userIds 筛选；筛选和排序题目必须存在，题型与操作符必须匹配；RANGE 至少提供一个边界且 min 不能大于 max。
                    - 处理：填写者条件始终与 filters 筛选树共同生效；filters 为空时不增加题目筛选；sorts 为空时按提交时间倒序，传入时仅采用指定排序；匿名问卷统一隐藏填写者身份，不对答案进行版本映射。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED；问卷主档不存在 -> QuestionnaireError.TABLE_NOT_FOUND；主档指向的发布版本不存在 -> QuestionnaireError.TABLE_VERSION_NOT_FOUND；查询条件不受支持或与当前题目不匹配 -> QuestionnaireError.SUBMISSION_QUERY_INVALID。
                    - 响应：返回答卷总数，以及当前页每份答卷的 ID、完整答案、提交时间和更新时间；非匿名问卷额外返回 userId。
                    """
    )
    @CheckRole
    @PostMapping("/listAllSubmissions")
    public R<PageR<QuestionnaireSubmissionResponse>> listAllSubmissions(@Validated @RequestBody QuestionnaireSubmissionListRequest request) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(request.getResourceId()).userId(SecurityContextHolder.getUserId()).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
        return R.ok(questionnaireSubmissionService.listAllSubmissions(request));
    }

    @Operation(
            summary = "删除答卷",
            description = """
                    - 用途：问卷管理者删除一份指定答卷，或清空问卷的全部答卷。
                    - 请求：resourceId 指定问卷；submissionId 可选，传入时删除该答卷，未传时删除全部答卷。
                    - 约束：当前用户必须已登录并拥有目标资源 EDIT 动作。
                    - 处理：按 resourceId 和可选 submissionId 物理删除答卷；不删除问卷主档和版本，没有匹配记录时仍正常成功。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；无 EDIT 权限 -> QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED。
                    - 响应：成功时返回空结果。
                    """
    )
    @CheckRole
    @Log(title = "删除答卷", businessType = BusinessType.DELETE)
    @PostMapping("/deleteSubmissions")
    public R<Void> deleteSubmissions(@RequestParam String resourceId, @RequestParam(required = false) String submissionId) {
        ResourceCheckPermissionResDTO permission = remoteResourceService.checkResPermission(ResourceCheckPermissionReqDTO.builder()
                .resourceId(resourceId).userId(SecurityContextHolder.getUserId()).groupRoles(SecurityContextHolder.getGroupRoleMap()).targetVersion(null).build()).getData();
        if (permission == null || permission.getAllowedActions() == null || !permission.getAllowedActions().contains(ResourceAction.EDIT)) {
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PERMISSION_DENIED);
        }
        questionnaireSubmissionService.deleteSubmissions(resourceId, submissionId);
        return R.ok();
    }
}
