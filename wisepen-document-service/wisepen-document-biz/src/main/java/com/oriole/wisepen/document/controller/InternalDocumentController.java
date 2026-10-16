package com.oriole.wisepen.document.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageResolveResponse;
import com.oriole.wisepen.document.api.feign.RemoteDocumentService;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.document.service.IDocumentAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "内部 - 文档", description = "供可信服务读取正文和按需解析文档图片")
@RequestMapping("/internal/document")
@RequiredArgsConstructor
public class InternalDocumentController implements RemoteDocumentService {

    private final IDocumentService documentService;
    private final IDocumentAssetService documentAssetService;

    @Operation(summary = "内部查询正文图片", description = """
            - 用途：供可信服务发现正文图片，按需提供给 Agent。
            - 请求：resourceId、可选 version、可选 pageNo。
            - 约束：仅内部服务调用；调用方负责选定版本的 VIEW 授权；文档已就绪。
            - 处理：复用正文解析，返回实际版本及推定页码，不申请下载地址。
            - 失败：文档不存在 -> DocumentError.DOCUMENT_NOT_FOUND；未就绪 -> DocumentError.DOCUMENT_PREVIEW_NOT_READY。
            - 响应：正文图片列表和全文未知页图片数。
            """)
    @GetMapping("/getDocumentImageReferences")
    @Override
    public R<DocumentImageReferencesResponse> getDocumentImageReferences(@RequestParam("resourceId") String resourceId,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "pageNo", required = false) Integer pageNo) {
        return R.ok(documentAssetService.listImageReferences(documentService.getDocumentVersion(resourceId, version), pageNo));
    }

    @Operation(summary = "内部解析文档图片", description = """
            - 用途：供可信服务按需获取单张图片的下载地址。
            - 请求：resourceId、可选 version、assetRef；内部允许平台链接或规范 objectKey。
            - 约束：仅内部服务调用；调用方应先选定 version 并完成该版本 VIEW 授权；图片必须被正文引用。
            - 处理：验证版本归属和实际引用后生成限时下载地址。
            - 失败：非法链接 -> CommonError.REQUEST_PARAM_INVALID；未引用图片 -> DocumentError.DOCUMENT_IMAGE_NOT_FOUND；下载地址不可用 -> DocumentError.DOCUMENT_DOWNLOAD_URL_APPLY_FAILED。
            - 响应：实际版本、objectKey、downloadUrl 和 expiresInSeconds。
            """)
    @GetMapping("/resolveDocumentImage")
    @Override
    public R<DocumentImageResolveResponse> resolveDocumentImage(@RequestParam("resourceId") String resourceId,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam("assetRef") String assetRef) {
        return R.ok(documentAssetService.resolveImage(documentService.getDocumentVersion(resourceId, version), assetRef));
    }

    @GetMapping("/getDocumentSearchText")
    @Override
    public R<DocumentSearchTextResponse> getDocumentSearchText(@RequestParam("resourceId") String resourceId,
                                                               @RequestParam(value = "version", required = false) Integer version) {
        return R.ok(documentService.getDocumentSearchText(resourceId, version));
    }
}
