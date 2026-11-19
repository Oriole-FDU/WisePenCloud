package com.oriole.wisepen.media.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.media.api.domain.dto.res.MediaInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 提供给其他微服务的媒体服务内部接口。
 */
@FeignClient(contextId = "remoteMediaService", value = "wisepen-media-service", path = "/internal/media")
public interface RemoteMediaService {

    @GetMapping("/getMediaInfo")
    R<MediaInfoResponse> getMediaInfo(@RequestParam("resourceId") String resourceId);
}
