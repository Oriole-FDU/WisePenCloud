package com.oriole.wisepen.user.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessDomain;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.user.api.domain.dto.req.MessagePublishRequest;
import com.oriole.wisepen.user.api.domain.dto.res.AdminMessageInfoResponse;
import com.oriole.wisepen.user.service.IMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员 - 站内信", description = "管理员发布站内信与公告")
@RestController
@RequestMapping("/admin/message")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminMessageController {

    private final IMessageService messageService;

    @Operation(
            summary = "分页查询站内信",
            description = """
                    - 用途：管理员查看所有已发布站内信，包括全员消息与定向投递消息。
                    - 请求：page 和 size 控制分页。
                    - 处理：直接分页查询消息主体，并返回每条消息的已读人数 readCount。
                    """
    )
    @GetMapping("/listMessages")
    @Log(title = "管理员分页查询站内信", businessType = BusinessType.SELECT, isSaveResponseData = false)
    public R<PageR<AdminMessageInfoResponse>> listMessages(
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size
    ) {
        return R.ok(messageService.listAdminMessages(page, size));
    }

    @Operation(
            summary = "发布站内信",
            description = """
                    - 用途：管理员向指定用户或全员发布站内信公告。
                    - 请求：deliveryScope=ALL_USERS 时 messageType 必须为 SYSTEM；deliveryScope=DIRECT 时 receiverUserIds 必须指定接收用户。
                    - 处理：由后端补充 sourceService 与 bizTraceId，并复用站内信发布服务创建消息。
                    """
    )
    @PostMapping("/publishMessage")
    @Log(title = "管理员发布站内信", businessType = BusinessType.INSERT, isSaveResponseData = false)
    public R<Void> publishMessage(@RequestBody MessagePublishRequest req) {
        req.setSourceService(BusinessDomain.SYSTEM);
        req.setBizTraceId("admin-announcement-" + IdWorker.getId());
        messageService.publishMessage(req);
        return R.ok();
    }
}
