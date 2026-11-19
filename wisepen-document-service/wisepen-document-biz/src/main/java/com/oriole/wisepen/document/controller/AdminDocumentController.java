package com.oriole.wisepen.document.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.document.api.enums.DocumentDownloadType;
import com.oriole.wisepen.document.service.IDocumentPreviewService;
import com.oriole.wisepen.resource.domain.dto.req.MarketSaleVersionCheckRequest;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
@Tag(name = "管理员 - 文档", description = "管理员查看集市文档审核文件")
@RestController
@RequestMapping("/admin/document")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
@Validated
public class AdminDocumentController {

    private final IDocumentPreviewService documentPreviewService;
    private final RemoteResourceService remoteResourceService;

    @Operation(
            summary = "获取集市文档原文件",
            description = """
                    - 用途：供平台管理员获取集市文档提交的原始文件，用于审核或管理。
                    - 请求：resourceId 与 marketGroupId 指定集市资源；offerVersion 指定要查看的售卖版本。
                    - 约束：当前用户必须是平台管理员；集市销售信息必须存在且 offerVersion 必须匹配当前版本；对应文档版本必须已处理完成。
                    - 处理：先向资源服务校验集市资源版本，再通过携带附件下载标记的对象存储临时签名地址重定向到上传的原始文件；不转换为预览 PDF，也不授予通用资源权限。
                    - 失败：当前身份不是平台管理员 -> PermissionError.UNAUTHORIZED；资源不存在 -> ResourceError.RESOURCE_NOT_FOUND；集市销售信息不存在 -> ResourceError.MARKET_SALE_INFO_NOT_FOUND；售卖版本已变化 -> ResourceError.MARKET_AUDIT_VERSION_CONFLICT；文档版本未就绪 -> DocumentError.DOCUMENT_PREVIEW_NOT_READY；原文件下载地址申请失败 -> DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED。
                    - 响应：返回 302 重定向到原始文件的限时下载地址，浏览器按附件下载原格式文件。
                    """
    )
    @Log(title = "管理员获取集市文档原文件", businessType = BusinessType.EXPORT, isSaveRequestData = false, isSaveResponseData = false)
    @GetMapping("/marketFile")
    public void getMarketFile(
            @RequestParam(value = "resourceId") @NotBlank String resourceId,
            @RequestParam(value = "marketGroupId") @NotBlank String marketGroupId,
            @RequestParam(value = "offerVersion") @Min(1) Integer offerVersion,
            HttpServletRequest request,
            HttpServletResponse response) {
        remoteResourceService.assertMarketSaleVersion(MarketSaleVersionCheckRequest.builder()
                .resourceId(resourceId)
                .marketGroupId(marketGroupId)
                .offerVersion(offerVersion)
                .build());

        String operatorId = SecurityContextHolder.getUserId().toString();
        log.info("admin market file download requested. resourceId={} marketGroupId={} offerVersion={} operatorId={}",
                resourceId, marketGroupId, offerVersion, operatorId);
        documentPreviewService.handleDownloadRequest(
                request, response, resourceId, offerVersion, operatorId, DocumentDownloadType.ORIGINAL);
    }
}
