package com.oriole.wisepen.questionnaire.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireResponse;
import com.oriole.wisepen.questionnaire.service.QuestionnaireService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/questionnaires")
@Tag(name = "问卷编制", description = "创建问卷草稿并读取当前问卷版本")
public class QuestionnaireController {
    private final QuestionnaireService questionnaireService;

    @Operation(
            summary = "创建问卷草稿",
            description = """
                    - 用途：为已有资源 ID 创建问卷主档和首个可编辑版本。
                    - 请求：resourceId 是调用方提供的全局唯一资源 ID；definition 是完整问卷结构；submissionPolicy 是填写规则。
                    - 约束：当前用户必须已登录；resourceId 不能重复；请求字段必须通过基础参数校验。
                    - 处理：写入问卷主档，并创建 version=1、status=DRAFT 的问卷版本；本接口不向资源服务注册资源，也不发布问卷。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；问卷已存在 -> QuestionnaireError.QUESTIONNAIRE_ALREADY_EXISTS。
                    - 响应：返回创建成功的问卷资源 ID。
                    """
    )
    @Log(title = "创建问卷草稿", businessType = BusinessType.INSERT, isSaveRequestData = false)
    @PostMapping
    public R<String> createQuestionnaire(@Valid @RequestBody QuestionnaireCreateRequest request) {
        return R.ok(questionnaireService.createQuestionnaire(request));
    }

    @Operation(
            summary = "获取当前问卷",
            description = """
                    - 用途：加载问卷主档当前版本的完整结构和填写规则。
                    - 请求：resourceId 指定目标问卷资源。
                    - 约束：当前用户必须已登录；问卷主档及其当前版本必须存在。
                    - 处理：先读取问卷主档中的当前版本号，再读取对应版本内容；不修改问卷状态或答卷数据。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN；问卷不存在 -> QuestionnaireError.QUESTIONNAIRE_NOT_FOUND；当前版本不存在 -> QuestionnaireError.QUESTIONNAIRE_VERSION_NOT_FOUND。
                    - 响应：返回当前版本号、版本状态、完整问卷定义和填写规则。
                    """
    )
    @GetMapping("/{resourceId}")
    public R<QuestionnaireResponse> getQuestionnaire(@PathVariable String resourceId) {
        return R.ok(questionnaireService.getQuestionnaire(resourceId));
    }
}
