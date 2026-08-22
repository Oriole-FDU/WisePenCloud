package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用资源详情响应
 */
@Data
@Builder
public class GenericResourceDetailResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ResourceItemResponse resourceInfo;
    private GenericResourceInfoResponse genericResourceFileInfo;
}
