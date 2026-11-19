package com.oriole.wisepen.note.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.service.INoteVersionService;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleVersionCheckRequest;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "管理员 - 笔记", description = "管理员查看集市笔记审核快照")
@RestController
@RequestMapping("/admin/note")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminNoteController {

    private final INoteVersionService noteVersionService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "查看集市笔记版本",
            description = """
                    - 用途：供平台管理员读取集市 NOTE 或 DRAWIO 的指定售卖版本快照，用于审核。
                    - 请求：resourceId 与 marketGroupId 指定集市资源；offerVersion 指定售卖版本。
                    - 约束：当前用户必须是平台管理员；集市销售信息必须存在且 offerVersion 必须匹配当前版本；笔记指定版本必须存在。
                    - 处理：先校验集市资源版本，再返回该版本对应的最近 FULL 快照及后续 DELTA 链；不修改笔记内容或资源权限。
                    - 失败：当前身份不是平台管理员 -> PermissionError.UNAUTHORIZED；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；集市销售信息不存在 -> ResourceError.MARKET_SALE_INFO_NOT_FOUND；售卖版本已变化 -> ResourceError.MARKET_AUDIT_VERSION_CONFLICT；笔记或指定版本不存在 -> NoteError.NOTE_NOT_FOUND。
                    - 响应：返回 Base64 编码的 fullSnapshot 和 deltas，供笔记客户端恢复该版本。
                    """
    )
    @Log(title = "管理员查看集市笔记版本", businessType = BusinessType.SELECT,
            isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/marketSnapshot")
    public R<NoteSnapshotResponse> getMarketSnapshot(
            @RequestParam(value = "resourceId") @NotBlank String resourceId,
            @RequestParam(value = "marketGroupId") @NotBlank String marketGroupId,
            @RequestParam(value = "offerVersion") @Min(1) Integer offerVersion) {
        remoteResourceService.assertMarketSaleVersion(MarketSaleVersionCheckRequest.builder()
                .resourceId(resourceId)
                .marketGroupId(marketGroupId)
                .offerVersion(offerVersion)
                .build());

        String operatorId = SecurityContextHolder.getUserId().toString();
        log.info("admin market note snapshot accessed. resourceId={} marketGroupId={} offerVersion={} operatorId={}",
                resourceId, marketGroupId, offerVersion, operatorId);
        return R.ok(noteVersionService.getVersion(resourceId, offerVersion));
    }
}
