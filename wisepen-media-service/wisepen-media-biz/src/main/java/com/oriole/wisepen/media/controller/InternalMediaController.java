package com.oriole.wisepen.media.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.media.api.constant.MediaValidationMsg;
import com.oriole.wisepen.media.api.domain.dto.res.MediaInfoResponse;
import com.oriole.wisepen.media.api.feign.RemoteMediaService;
import com.oriole.wisepen.media.service.IMediaService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/media")
@RequiredArgsConstructor
@Validated
public class InternalMediaController implements RemoteMediaService {

    private final IMediaService mediaService;

    @GetMapping("/getMediaInfo")
    @Override
    public R<MediaInfoResponse> getMediaInfo(
            @RequestParam("resourceId") @NotBlank(message = MediaValidationMsg.RESOURCE_ID_EMPTY) String resourceId) {
        return R.ok(mediaService.getMediaInfo(resourceId));
    }
}
