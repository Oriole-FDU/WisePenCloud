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
@Tag(name = "媒体内部接口", description = "供其他微服务查询媒体处理结果")
public class InternalMediaController implements RemoteMediaService {

    private final IMediaService mediaService;

    @GetMapping("/getMediaInfo")
    @Override
    @Operation(summary = "查询媒体信息", description = "根据资源 ID 查询媒体处理信息；资源不存在时返回 MediaError.MEDIA_NOT_FOUND。")
    public R<MediaInfoResponse> getMediaInfo(
            @RequestParam("resourceId") String resourceId) {
        return R.ok(mediaService.getMediaInfo(resourceId));
    }
}
