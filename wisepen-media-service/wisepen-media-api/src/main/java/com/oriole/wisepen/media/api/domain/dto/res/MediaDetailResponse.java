package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaDetailResponse {

    private ResourceItemResponse resourceInfo;

    private MediaInfoResponse mediaInfo;
}