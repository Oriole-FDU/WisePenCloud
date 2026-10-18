package com.oriole.wisepen.document.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentImageResolveResponse {
    private String resourceId;
    private String documentId;
    private Integer version;
    private String objectKey;
    private String downloadUrl;
    private long expiresInSeconds;
}

