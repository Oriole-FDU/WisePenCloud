package com.oriole.wisepen.media.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
/**
 * 提供给其他微服务的媒体服务内部接口。
 */
@FeignClient(contextId = "remoteMediaService", value = "wisepen-media-service", path = "/internal/media")
public interface RemoteMediaService {
}
