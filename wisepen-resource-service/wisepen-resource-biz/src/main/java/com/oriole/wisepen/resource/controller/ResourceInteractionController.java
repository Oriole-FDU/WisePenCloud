package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.dto.req.ResourceRateRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceLikeRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceReadRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceUserInteractionRecordResponse;
import com.oriole.wisepen.resource.service.IResourceInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "资源互动", description = "资源阅读、点赞、评分与用户互动状态")
@RestController
@RequestMapping("/resource/interaction")
@RequiredArgsConstructor
@CheckRole
@Validated
public class ResourceInteractionController {

    private final IResourceInteractionService resourceInteractionService;

    @Operation(
            summary = "获取资源互动状态",
            description = """
                    - 用途：查询当前用户对指定资源的阅读、点赞和评分记录。
                    - 请求：resourceId 指定目标资源。
                    - 约束：当前用户必须已登录；本接口按用户与资源 ID 查询互动记录，不承担资源可见性校验。
                    - 处理：读取当前用户的资源互动记录；若不存在记录则返回默认未读、未点赞、未评分状态；不更新资源全局统计。
                    - 失败：无业务失败点。
                    - 响应：返回当前用户对该资源的互动状态。
                    """
    )
    @GetMapping("/getResourceUserInteractionRecord")
    public R<ResourceUserInteractionRecordResponse> getResourceUserInteractionRecord(
            @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK) @RequestParam String resourceId) {
        String userId = SecurityContextHolder.getUserId().toString();
        return R.ok(resourceInteractionService.getResourceUserInteractionInfo(resourceId, userId));
    }

    @Operation(
            summary = "记录资源阅读",
            description = """
                    - 用途：在用户打开或阅读资源时记录一次有效阅读行为。
                    - 请求：resourceId 指定被阅读资源。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被删除。
                    - 处理：通过 Redis 窗口判断当前用户是否为窗口期内首次阅读；首次阅读会写入用户已读记录并增加资源阅读数，重复阅读不重复累加。
                    - 失败：资源不存在或已被删除 -> ResourceError.RESOURCE_NOT_FOUND；Redis 读写异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "资源阅读", businessType = BusinessType.UPDATE)
    @PostMapping("/read")
    public R<Void> changeResourceReadStatus(@Validated @RequestBody ResourceReadRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        resourceInteractionService.changeResourceReadStatus(request, userId, groupRoles);
        return R.ok();
    }

    @Operation(
            summary = "切换资源点赞状态",
            description = """
                    - 用途：让当前用户对资源执行点赞或取消点赞。
                    - 请求：resourceId 指定目标资源。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被删除。
                    - 处理：读取当前用户点赞状态并取反，写入新的点赞状态；点赞时资源点赞数加一，取消点赞时资源点赞数减一；不影响评分和阅读状态。
                    - 失败：资源不存在或已被删除 -> ResourceError.RESOURCE_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "资源点赞", businessType = BusinessType.UPDATE)
    @PostMapping("/toggleLike")
    public R<Void> changeResourceLikeStatus(@Validated @RequestBody ResourceLikeRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        Map<Long, GroupRoleType> groupRoles = SecurityContextHolder.getGroupRoleMap();
        resourceInteractionService.changeResourceLikeStatus(request, userId, groupRoles);
        return R.ok();
    }

    @Operation(
            summary = "评价资源",
            description = """
                    - 用途：记录或更新当前用户对资源的评分。
                    - 请求：resourceId 指定目标资源；score 为用户提交的评分值。
                    - 约束：当前用户必须已登录；目标资源必须存在且未被删除；score 取值范围为 1 到 5。
                    - 处理：写入当前用户评分；首次评分会增加评分人数和总分，修改评分只调整总分差值，相同评分不会重复改变统计；不影响点赞和阅读状态。
                    - 失败：资源不存在或已被删除 -> ResourceError.RESOURCE_NOT_FOUND。
                    - 响应：成功时返回空结果。
                    """
    )
    @Log(title = "资源评分", businessType = BusinessType.UPDATE)
    @PostMapping("/rate")
    public R<Void> changeResourceScore(@Validated @RequestBody ResourceRateRequest request) {
        String userId = SecurityContextHolder.getUserId().toString();
        resourceInteractionService.changeResourceScore(request, userId);
        return R.ok();
    }
}
