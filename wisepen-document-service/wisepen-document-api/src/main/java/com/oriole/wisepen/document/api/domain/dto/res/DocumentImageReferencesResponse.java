package com.oriole.wisepen.document.api.domain.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentImageReferencesResponse {
    private String resourceId;
    private String documentId;
    private Integer version;
    private List<ImageReference> images;
    /** 按页筛选时仍返回全文未知页的图片数。 */
    private long unknownPageImageCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageReference {
        private int imageIndex;
        private String objectKey;
        private String markdownLink;
        private String alt;
        private Integer pageNo;
        private PageLocation pageLocation;
    }

    public enum PageLocation {
        INFERRED, UNKNOWN
    }
}

