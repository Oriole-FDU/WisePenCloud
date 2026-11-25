package com.oriole.wisepen.system.api.domain.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackDetailResponse extends FeedbackListItemResponse {
    private String content;

    private String contact;

    private String imageUrl;

    private String browser;
}
