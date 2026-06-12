package com.oriole.wisepen.ai.asset.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadInitResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String resourceId;
    private Integer version;

    @Builder.Default
    private List<AssetUploadTicket> assetUploadTickets = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetUploadTicket implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String assetId;
        private String path;
        private String name;
        private String objectKey;
        private String putUrl;
        private String callbackHeader;
        private Boolean flashUploaded;
    }
}
