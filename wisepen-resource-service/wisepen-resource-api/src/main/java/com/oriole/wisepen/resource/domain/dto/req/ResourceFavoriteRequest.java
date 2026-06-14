package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ResourceFavoriteRequest {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    // 收藏状态
    @NotNull(message = ResourceValidationMsg.FAVORITE_STATUS_NOT_NULL)
    private Boolean favorite;

    // 收藏夹（为空时自动加入默认收藏夹）
    private List<String> collectionIds;
}
