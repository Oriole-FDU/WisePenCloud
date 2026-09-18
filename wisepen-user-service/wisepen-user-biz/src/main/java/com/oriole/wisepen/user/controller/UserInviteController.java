package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.res.UserInviteRecordResponse;
import com.oriole.wisepen.user.service.IUserInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户邀请", description = "用户邀请关系与奖励兑现记录")
@RestController
@RequestMapping("/user/invite")
@RequiredArgsConstructor
public class UserInviteController {

    private final IUserInviteService userInviteService;

    @Operation(
            summary = "分页查询我的邀请记录",
            description = """
                    - 用途：查询当前用户发出的邀请关系记录。
                    - 请求：page 和 size 控制分页。
                    - 约束：当前用户必须已登录。
                    - 处理：按邀请创建时间倒序分页读取邀请关系，并补充被邀请人展示信息。
                    - 失败：未登录 -> PermissionError.NOT_LOGIN。
                    - 响应：返回分页邀请记录、被邀请人展示信息、邀请状态和奖励兑现时间。
                    """
    )
    @CheckLogin
    @GetMapping("/listRecords")
    public R<PageR<UserInviteRecordResponse>> listInviteRecords(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size
    ) {
        return R.ok(userInviteService.listInviteRecords(SecurityContextHolder.getUserId(), page, size));
    }
}
