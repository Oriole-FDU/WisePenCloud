package com.oriole.wisepen.media.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.media.api.domain.dto.res.MediaInfoResponse;
import com.oriole.wisepen.media.api.feign.RemoteMediaService;
import com.oriole.wisepen.media.service.IMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/media")
@RequiredArgsConstructor
@Tag(name = "内部 - 媒体", description = "供业务微服务查询媒体处理信息")
public class InternalMediaController implements RemoteMediaService {

    private final IMediaService mediaService;

    @GetMapping("/getMediaInfo")
    @Override
    @Operation(
            summary = "内部查询媒体信息",
            description = """
                    - 用途：供业务微服务组合媒体资源信息与处理结果。
                    - 请求：resourceId 指定媒体资源。
                    - 约束：调用方必须通过内部服务调用边界；目标媒体必须存在。
                    - 处理：查询媒体记录并返回处理状态和产物信息；不刷新状态或触发处理。
                    - 失败：媒体不存在 -> MediaError.MEDIA_NOT_FOUND。
                    - 响应：返回媒体类型、处理状态、源文件与预览产物信息。
                    """
    )
    public R<MediaInfoResponse> getMediaInfo(
            @RequestParam("resourceId") String resourceId) {
        return R.ok(mediaService.getMediaInfo(resourceId));
    }
}
