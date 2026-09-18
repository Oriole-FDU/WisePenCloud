package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskCheckInResponse;
import com.oriole.wisepen.user.api.domain.dto.res.UserTaskStatusResponse;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.service.IUserTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户任务", description = "用户任务完成与奖励")
@RestController
@RequestMapping("/user/task")
@RequiredArgsConstructor
public class UserTaskController {

    private final IUserTaskService userTaskService;

    @Operation(
            summary = "查询任务完成状态",
            description = """
                    - 用途：查询当前用户各项任务在当前规则下的完成状态与奖励展示信息。
                    - 请求：无需请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录。
                    - 处理：按任务处理器声明的限制规则统计当前用户任务完成情况，并返回任务是否可继续完成。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回任务类型、启用状态、是否可完成、奖励信息，以及单次任务或周期任务的完成详情。
                    """
    )
    @CheckLogin
    @GetMapping("/listTaskStatus")
    public R<List<UserTaskStatusResponse>> listTaskStatus() {
        return R.ok(userTaskService.listTaskStatus(SecurityContextHolder.getUserId()));
    }

    @Operation(
            summary = "每日签到",
            description = """
                    - 用途：当前用户完成每日签到并获得随机奖励。
                    - 请求：无需请求参数，目标用户来自当前认证上下文。
                    - 约束：当前用户必须已登录；同一刷新周期内重复签到不重复发奖。
                    - 处理：按配置计算随机奖励与周期保底，并写入任务记录和对应钱包流水。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回本次或当前周期内已有签到的奖励、保底命中状态和周期进度。
                    """
    )
    @CheckLogin
    @PostMapping("/dailyCheckIn")
    public R<UserTaskCheckInResponse> dailyCheckIn() {
        return R.ok((UserTaskCheckInResponse) userTaskService.complete(
                SecurityContextHolder.getUserId(),
                UserTaskCode.DAILY_CHECK_IN,
                SecurityContextHolder.getUserId(),
                null
        ));
    }
}
