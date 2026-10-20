package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 ID")
    private String resourceId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "适用的起始版本")
    private Integer applicableFromVersion; // 适用的起始版本

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int32", description = "适用的终结版本")
    private Integer applicableToVersion; // 适用的终结版本

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "创建者用户 ID")
    private String creatorId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "行内评论锚点")
    private AnchorRef anchorRef; // 锚点

    @Builder.Default
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否已解决")
    private Boolean resolved = false;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "解决人用户 ID")
    private String resolvedBy;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, type = "number", format = "int64", description = "解决时间")
    private LocalDateTime resolvedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnchorRef {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "外部锚点 ID")
        private String externalAnchorId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "锚点引用文本")
        private String quoteText;

        @Builder.Default
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "锚点附加数据")
        private Map<String, Object> anchorPayload = new HashMap<>();
    }
}
