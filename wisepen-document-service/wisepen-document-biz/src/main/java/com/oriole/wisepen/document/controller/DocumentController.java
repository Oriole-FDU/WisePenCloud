package com.oriole.wisepen.document.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.service.IDocumentDownloadService;
import com.oriole.wisepen.document.service.IDocumentPreviewService;
import com.oriole.wisepen.document.service.IDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文档管理接口（对外开放）
 *
 * @author Ian.xiong
 */
@RestController
@RequestMapping("/document")
@RequiredArgsConstructor
public class DocumentController {

    private final IDocumentService documentService;
    private final IDocumentPreviewService documentPreviewService;
    private final IDocumentDownloadService documentDownloadService;

    /**
     * 上传初始化：前端提交 md5/filename/extension/expectedSize，
     * 后端返回 OSS 预签名直传 URL，前端直接将文件字节流 PUT 到 OSS。
     */
    @PostMapping("/upload/init")
    public R<DocumentUploadInitResponse> initUpload(@Valid @RequestBody DocumentUploadInitRequest request) {
        Long uploaderId = SecurityContextHolder.getUserId();
        return R.ok(documentService.initUpload(request, uploaderId));
    }

    /**
     * 重试转换：仅在文档处于 FAILED 状态时可调用，用于转换失败后的人工重试。
     */
    @PostMapping("/{documentId}/retry")
    public R<Void> retryConvert(@PathVariable String documentId) {
        documentService.retryConvert(documentId);
        return R.ok();
    }

    /**
     * 获取带双层水印的预览 PDF（支持 HTTP Range 请求）。
     *
     * <p>采用 O(1) 预埋 + 增量更新模式：
     * <ul>
     *   <li>虚拟文件 = OSS 原始预埋 PDF + 动态水印附录（约 10 KB）。</li>
     *   <li>Range 落在原始段：零内存管道透传 OSS，不加载整个 PDF。</li>
     *   <li>Range 落在附录段：内存生成 ~10 KB 增量附录（含真实 userId + 时间戳）后切片返回。</li>
     * </ul>
     *
     * <p>明水印：userId + 预览时间戳，45° 斜向；暗水印：AES-128 二值矩阵 9×9 平铺，透明度 1%。<br>
     * 仅 {@code READY} 状态的文档可预览。
     */
    @GetMapping("/{documentId}/preview")
    public void preview(@PathVariable String documentId,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        String userId = String.valueOf(SecurityContextHolder.getUserId());
        documentPreviewService.handlePreviewRequest(request, response, documentId, userId);
    }

    /**
     * 下载原始文档（302 重定向至 OSS 预签名 URL，有效期 900 秒）。
     * <p>仅 {@code READY} 状态的文档可下载，返回用户上传的原始文件（不含水印）。
     */
    @GetMapping("/{documentId}/download")
    public void download(@PathVariable String documentId,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        String userId = String.valueOf(SecurityContextHolder.getUserId());
        documentDownloadService.handleDownloadRequest(request, response, documentId, userId);
    }

    /**
     * 删除文档或取消上传。
     * <p>
     * 在任意阶段均可调用：上传中（取消上传）、转换中（异步退出）、已就绪（删除）。
     * 调用方须已通过鉴权中间件的身份校验。
     * </p>
     */
    @DeleteMapping("/{documentId}")
    public R<Void> cancelOrDelete(@PathVariable String documentId) {
        documentService.cancelOrDelete(documentId);
        return R.ok();
    }
}
