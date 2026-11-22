package com.oriole.wisepen.media.api.domain.dto.res;

import com.oriole.wisepen.media.api.domain.base.MediaBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MediaResponse extends MediaBase {
    private String mediaId;
}
