package com.oriole.wisepen.generic.resource.api.domain.dto.res;

import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 通用资源详情响应
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GenericResourceDetailResponse {

    private ResourceItemResponse resourceInfo;
    private GenericResourceInfoResponse genericResourceFileInfo;
}