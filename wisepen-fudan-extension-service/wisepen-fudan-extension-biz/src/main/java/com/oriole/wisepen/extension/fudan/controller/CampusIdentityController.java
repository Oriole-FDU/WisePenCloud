package com.oriole.wisepen.extension.fudan.controller;

import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityChallengeSubmitRequest;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityInitiateRequest;
import com.oriole.wisepen.campus.extension.api.feign.RemoteIdentityService;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.extension.fudan.exception.FudanExtensionError;
import com.oriole.wisepen.extension.fudan.service.FudanUISAuthTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@Tag(name = "内部 - 校园身份认证", description = "复旦 UIS 标准校园身份认证接口")
@RestController
@RequestMapping("/internal/campusIdentity")
@RequiredArgsConstructor
public class CampusIdentityController implements RemoteIdentityService {

    private final FudanUISAuthTaskService fudanUISAuthTaskService;

    @Operation(
            summary = "获取复旦 UIS Provider 描述",
            description = """
                    - 用途：让用户服务和前端了解复旦 UIS Provider 支持的输入项、学校信息和登录 Challenge 类型。
                    - 请求：无需请求参数。
                    - 约束：调用方必须通过内部服务调用边界访问本接口。
                    - 处理：返回复旦扩展服务声明的标准 Provider 能力；不创建认证任务，也不接收 UIS 凭据。
                    - 失败：Provider 描述组装或序列化发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回 Provider 编码、学校信息、所需输入字段和支持的登录 Challenge 类型。
                    """
    )
    @GetMapping("/getProviderDescriptor")
    public R<CampusIdentityProviderDescriptorDTO> getProviderDescriptor() {
        return R.ok(fudanUISAuthTaskService.getProviderDescriptor());
    }

    @Operation(
            summary = "发起复旦 UIS 校园身份认证任务",
            description = """
                    - 用途：受理一次复旦 UIS 校园身份认证，并启动扩展服务内部的异步浏览器任务。
                    - 请求：userId 指定 WisePen 用户；providerCode 必须为 FUDAN_UIS；inputs 携带 account 和 password。
                    - 约束：调用方必须通过内部服务调用边界访问本接口；UIS 凭据只在复旦扩展服务内部使用，不写入任务快照。
                    - 处理：校验请求后保存 PENDING 状态并异步执行 UIS 登录；不会把密码写入 Redis 或标准身份 claims。
                    - 失败：发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回已受理的标准校园身份任务状态。
                    """
    )
    @PostMapping("/initiateVerifyTask")
    public R<CampusIdentityTaskResultDTO> initiateVerifyTask(@Valid @RequestBody CampusIdentityInitiateRequest request) {
        String taskId = fudanUISAuthTaskService.initiateVerifyTask(request);
        return R.ok(fudanUISAuthTaskService.checkVerifyTask(taskId, request.getUserId()));
    }

    @Operation(
            summary = "提交复旦 UIS 登录 Challenge",
            description = """
                    - 用途：拒绝对复旦 UIS 扫码 Challenge 的同步提交。
                    - 请求：userId、providerCode、taskId 和 challengeId 必须来自当前任务。
                    - 约束：复旦 UIS Challenge 的完成方式为 OUT_OF_BAND，客户端应轮询任务状态。
                    - 处理：始终返回 UIS_REQUEST_INVALID，提示调用方无需提交扫码结果。
                    - 失败：同步提交扫码 Challenge -> FudanExtensionError.UIS_REQUEST_INVALID。
                    - 响应：无成功响应。
                    """
    )
    @PostMapping("/submitLoginChallenge")
    public R<CampusIdentityTaskResultDTO> submitLoginChallenge(@Valid @RequestBody CampusIdentityChallengeSubmitRequest request) {
        throw new ServiceException(
                FudanExtensionError.UIS_REQUEST_INVALID,
                "复旦 UIS 二维码 Challenge 应通过轮询完成，无需提交"
        );
    }

    @Operation(
            summary = "检查复旦 UIS 校园身份认证任务状态",
            description = """
                    - 用途：查询指定用户的复旦 UIS 异步认证任务，并在成功时返回标准化身份 claims。
                    - 请求：taskId 和 userId 指定一次具体认证尝试；旧复旦入口可只传 userId 查询该用户最近一次任务。
                    - 约束：调用方必须通过内部服务调用边界访问本接口；任务快照必须仍保留在 Redis 中。
                    - 处理：读取复旦 UIS 内部任务快照并转换为标准校园身份任务结果。
                    - 失败：发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回任务状态；认证成功时附带标准化身份 claims。
                    """
    )
    @GetMapping("/checkVerifyTaskStatus")
    public R<CampusIdentityTaskResultDTO> checkVerifyTaskStatus(
            @RequestParam(value = "taskId", required = false) String taskId, @RequestParam Long userId
    ) {
        return R.ok(fudanUISAuthTaskService.checkVerifyTask(taskId, userId));
    }
}
