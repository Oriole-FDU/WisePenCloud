package com.oriole.wisepen.system.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.system.api.domain.dto.req.BatchTaskCreateRequest;
import com.oriole.wisepen.system.api.domain.dto.res.BatchTaskItemResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BatchTaskResponse;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import com.oriole.wisepen.system.service.BatchTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员 - 批量任务", description = "管理员创建、启动、停止、删除和查询通用批量任务")
@RestController
@RequestMapping("/system/admin/task")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminBatchTaskController {

    private final BatchTaskService batchTaskService;

    @Operation(
            summary = "创建批量任务",
            description = """
                    - 用途：创建由 type 选择执行器、meta 表达业务参数的通用批量任务，并生成独立任务项。
                    - 请求：type 指定任务执行器；name 是管理端展示名称；meta 由对应执行器校验。RESOURCE_FORK 接收一个 NOTE 或 DRAWIO 源资源、固定版本、目标用户列表和统一文件夹路径。USER_REGISTER 使用 users 数组传入 userId、用户账号字段和可选 profile 资料；userId 可为空，未提供时由用户服务生成。不接受密码、邀请码、钱包、生成后的个人邀请码或时间字段。
                    - 约束：当前身份必须是平台管理员；type 必须存在对应执行器；meta 必须满足执行器契约；单任务项数不能超过服务配置上限。USER_REGISTER 在创建任务时校验认证信息组合：有学工号时必须指定认证方式且账号不能为 UNIDENTIFIED；校园身份认证必须同时提供学工号和邮箱；邮箱认证必须提供邮箱。其余单个用户字段由用户服务在任务项执行时校验。
                    - 处理：执行器按 users 拆分独立任务项，在一个本地事务中保存 CREATED 任务。任务项执行时通过用户服务内部接口创建基础用户，再依次调用内部账号信息和资料更新接口设置用户信息。每次请求创建独立任务，本接口不发起执行。
                    - 失败：任务类型无执行器 -> SysError.BATCH_TASK_TYPE_UNSUPPORTED；meta 结构非法 -> SysError.BATCH_TASK_META_INVALID；USER_REGISTER 用户数超过上限 -> BatchTaskError.USER_REGISTER_BATCH_SIZE_EXCEEDED；源资源不存在 -> BatchTaskError.RESOURCE_FORK_SOURCE_NOT_FOUND；源资源类型暂不支持 -> BatchTaskError.RESOURCE_FORK_TYPE_UNSUPPORTED。
                    - 响应：返回任务ID。
                    """
    )
    @Log(title = "创建批量任务", businessType = BusinessType.INSERT,
            isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping("/createTask")
    public R<Long> createTask(@Valid @RequestBody BatchTaskCreateRequest request) {
        return R.ok(batchTaskService.createTask(request, SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "启动批量任务",
            description = """
                    - 用途：首次启动批量任务，或者恢复正在停止及已经停止的批量任务。
                    - 请求：taskId 指定目标任务。
                    - 约束：当前身份必须是平台管理员；任务必须存在且未删除；CREATED、PAUSING 或 PAUSED 状态可启动，PENDING 或 RUNNING 按幂等成功处理。
                    - 处理：CREATED 任务进入 PENDING；PAUSING 或 PAUSED 任务恢复为 RUNNING；仅继续处理 PENDING 任务项，不重试 FAILED 任务项；如果所有任务项已经结束则直接汇总最终状态。
                    - 失败：任务不存在或已删除 -> SysError.BATCH_TASK_NOT_FOUND；任务已处于终态 -> SysError.BATCH_TASK_STATE_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "启动批量任务", businessType = BusinessType.UPDATE)
    @PostMapping("/startTask")
    public R<Void> startTask(@RequestParam Long taskId) {
        batchTaskService.startTask(taskId);
        return R.ok();
    }

    @Operation(
            summary = "停止批量任务",
            description = """
                    - 用途：停止批量任务继续领取新的任务项，并等待已经开始的任务项执行结束。
                    - 请求：taskId 指定目标任务。
                    - 约束：当前身份必须是平台管理员；任务必须存在且未删除；仅 PENDING 或 RUNNING 任务可停止，PAUSING 或 PAUSED 按幂等成功处理。
                    - 处理：任务先进入 PAUSING；不再领取新的 PENDING 任务项；现有 RUNNING 任务项结束后，有待执行项则进入 PAUSED，全部任务项已结束则直接汇总最终状态。
                    - 失败：任务不存在或已删除 -> SysError.BATCH_TASK_NOT_FOUND；任务未开始或已处于终态 -> SysError.BATCH_TASK_STATE_INVALID。
                    - 响应：成功时返回空结果；返回时任务可能仍处于 PAUSING。
                    """
    )
    @Log(title = "停止批量任务", businessType = BusinessType.UPDATE)
    @PostMapping("/stopTask")
    public R<Void> stopTask(@RequestParam Long taskId) {
        batchTaskService.stopTask(taskId);
        return R.ok();
    }

    @Operation(
            summary = "删除批量任务",
            description = """
                    - 用途：从管理端软删除不再需要展示的批量任务，并保留任务及任务项审计数据。
                    - 请求：taskId 指定目标任务。
                    - 约束：当前身份必须是平台管理员；仅 CREATED、PAUSED 或终态任务可删除；PENDING、RUNNING 和 PAUSING 任务必须先完成停止流程。
                    - 处理：记录删除时间和操作管理员；不删除任务项，不停止仍在执行的调用，也不撤销任务已经产生的业务结果。
                    - 失败：任务不存在或已删除 -> SysError.BATCH_TASK_NOT_FOUND；任务状态不允许删除 -> SysError.BATCH_TASK_STATE_INVALID。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "删除批量任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/deleteTask")
    public R<Void> deleteTask(@RequestParam Long taskId) {
        batchTaskService.deleteTask(taskId, SecurityContextHolder.getUserId());
        return R.ok();
    }

    @Operation(
            summary = "查询批量任务",
            description = """
                    - 用途：查询批量任务的当前生命周期状态、规范化业务参数和执行进度。
                    - 请求：taskId 指定目标任务。
                    - 约束：当前身份必须是平台管理员；任务必须存在且未删除。
                    - 处理：读取任务主记录，并按任务项状态实时聚合待执行、执行中、成功和失败数量；不修改任务状态。
                    - 失败：任务不存在或已删除 -> SysError.BATCH_TASK_NOT_FOUND。
                    - 响应：返回任务信息、meta、各状态数量和生命周期时间。
                    """
    )
    @GetMapping("/getTaskInfo")
    public R<BatchTaskResponse> getTaskInfo(@RequestParam Long taskId) {
        return R.ok(batchTaskService.getTaskInfo(taskId));
    }

    @Operation(
            summary = "分页查询批量任务项",
            description = """
                    - 用途：分页查看批量任务中每个业务目标的执行状态、结果或失败原因。
                    - 请求：taskId 指定任务；status 可选，用于筛选任务项状态；page 和 size 控制分页，size 最大为 100。
                    - 约束：当前身份必须是平台管理员；任务必须存在且未删除。
                    - 处理：按任务项 ID 升序分页读取，解析通用 meta 和 result；不重试或修改任务项。
                    - 失败：任务不存在或已删除 -> SysError.BATCH_TASK_NOT_FOUND。
                    - 响应：返回任务项分页、状态、结构化参数与结果、错误码和生命周期时间。
                    """
    )
    @GetMapping("/listTaskItems")
    public R<PageR<BatchTaskItemResponse>> listTaskItems(
            @RequestParam Long taskId,
            @RequestParam(required = false) BatchTaskItemStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return R.ok(batchTaskService.listTaskItems(taskId, status, page, size));
    }
}
