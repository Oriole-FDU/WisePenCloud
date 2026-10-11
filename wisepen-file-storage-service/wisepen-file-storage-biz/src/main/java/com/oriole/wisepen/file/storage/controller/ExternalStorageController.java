package com.oriole.wisepen.file.storage.controller;

import com.oriole.wisepen.file.storage.service.IStorageService; // 根据你的实际命名调整
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 外部存储服务接口 (直接对外网暴露，处理前端图床直传和云厂商回调)
 */
@Tag(name = "外部 - 文件存储", description = "处理云存储服务回调")
@Slf4j
@RestController
@RequestMapping("/external/storage/")
@RequiredArgsConstructor
public class ExternalStorageController {

    private final IStorageService storageService;

    @Operation(
            summary = "接收上传完成回调",
            description = """
                    - 用途：接收对象存储服务在直传文件完成后的服务端回调。
                    - 请求：rawBody 携带 objectKey、md5 和 size 等回调参数；请求头携带云厂商签名信息。
                    - 约束：回调必须通过存储提供方签名校验；objectKey 必须对应已初始化的上传记录。
                    - 处理：校验签名后更新文件记录为 AVAILABLE，写入 md5 和文件大小，并发布文件上传完成事件；重复回调会被识别并忽略。
                    - 失败：回调签名非法 -> FileStorageError.STORAGE_PROVIDER_CALLBACK_SIGNATURE_INVALID；文件记录缺失或回调参数异常导致未处理失败 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回云厂商约定的 JSON 字符串。
                    """
    )
    @PostMapping("/callback/upload")
    @WithSpan("storage.upload.callback")
    public String handleUploadCallback(HttpServletRequest request, @RequestBody(required = false) String rawBody) {
        log.info("storage callback received. uri={}", request.getRequestURI());
        storageService.handleUploadCallback(request, rawBody);
        return "{\"Status\":\"OK\"}";
    }
}
