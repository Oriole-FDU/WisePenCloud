package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Full Update 语义：每次调用必须同时提供 collectionName 和 description（description 可为 null 表示清除描述）
 */
@Data
public class FavoriteCollectionInfoUpdateRequest {
    @NotBlank(message = ResourceValidationMsg.COLLECTION_ID_NOT_BLANK)
    private String collectionId;

    @NotBlank(message = ResourceValidationMsg.COLLECTION_NAME_NOT_BLANK)
    private String collectionName;

    /** 可选描述；传 null 表示清除描述 */
    private String description;
}
