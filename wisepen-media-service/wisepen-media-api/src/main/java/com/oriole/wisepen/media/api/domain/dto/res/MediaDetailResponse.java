package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class MediaDetailResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ResourceItemResponse resourceInfo;

    private MediaInfoResponse mediaInfo;
}