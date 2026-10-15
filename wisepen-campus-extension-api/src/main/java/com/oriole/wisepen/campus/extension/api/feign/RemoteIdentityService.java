package com.oriole.wisepen.campus.extension.api.feign;

import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityChallengeSubmitRequest;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityInitiateRequest;
import com.oriole.wisepen.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 校园身份 Provider 服务必须实现的标准 HTTP 协议。
 *
 * <p>调用方根据 Provider 配置动态创建 Feign 客户端，因此协议本身不绑定固定服务名。</p>
 */
public interface RemoteIdentityService {

    @Operation(
            summary = "获取校园身份认证 Provider 描述",
            description = """
                    - 用途：让用户服务和前端了解校园身份认证 Provider 支持的输入项、学校信息和登录 Challenge 类型
                    - 请求：无需请求参数。
                    - 约束：调用方必须通过内部服务调用边界访问本接口。
                    - 处理：返回扩展服务声明的标准 Provider 能力。
                    - 失败：Provider 描述组装或序列化发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回 Provider 编码、学校信息、所需输入字段和支持的登录 Challenge 类型。
                    """
    )
    @GetMapping("/internal/campusIdentity/getProviderDescriptor")
    R<CampusIdentityProviderDescriptorDTO> getProviderDescriptor();

    @Operation(
            summary = "发起校园身份认证任务",
            description = """
                    - 用途：受理一次校园身份认证。
                    - 请求：userId 指定 WisePen 用户；providerCode 为当前服务声明的 CAMPUS_EXTENSION_CODE；payload 携带校园身份认证 Provider 支持的输入项。
                    - 约束：调用方必须通过内部服务调用边界访问本接口。
                    - 处理：初始化校园认证业务。
                    - 失败：发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回校园身份任务状态，认证成功时附带标准化身份 claims。
                    """
    )
    @PostMapping("/internal/campusIdentity/initiateVerifyTask")
    R<CampusIdentityTaskResultDTO> initiateVerifyTask(@Valid @RequestBody CampusIdentityInitiateRequest request);

    @Operation(
            summary = "提交登录 Challenge（可选）",
            description = """
                    - 用途：接收登录 Challenge 的同步请求，并返回认证任务最新状态。
                    - 请求：userId、providerCode、taskId 和 challengeId 必须来自当前任务。
                    - 约束：调用方必须通过内部服务调用边界访问本接口；任务和 Challenge 标识必须匹配当前用户。
                    - 处理：校验任务与 Challenge 标识后完成校园认证业务。
                    - 失败：发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回校园身份任务状态。
                    """
    )
    @PostMapping("/internal/campusIdentity/submitLoginChallenge")
    R<CampusIdentityTaskResultDTO> submitLoginChallenge(@Valid @RequestBody CampusIdentityChallengeSubmitRequest request);

    @Operation(
            summary = "检查校园身份认证任务状态（可选）",
            description = """
                    - 用途：查询指定用户的异步认证任务，并在成功时返回标准化身份 claims。
                    - 请求：taskId 和 userId 指定一次具体认证尝试。
                    - 约束：调用方必须通过内部服务调用边界访问本接口。
                    - 处理：读取校园认证业务状态并返回结果。
                    - 失败：发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：返回任务状态；认证成功时附带标准化身份 claims。
                    """
    )
    @GetMapping("/internal/campusIdentity/checkVerifyTaskStatus")
    R<CampusIdentityTaskResultDTO> checkVerifyTaskStatus(
            @RequestParam(value = "taskId", required = false) String taskId,
            @RequestParam("userId") Long userId);
}
