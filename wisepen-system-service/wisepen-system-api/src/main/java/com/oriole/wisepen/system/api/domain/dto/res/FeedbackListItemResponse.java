package com.oriole.wisepen.system.api.domain.dto.res;

import com.oriole.wisepen.system.api.enums.FeedbackStatus;
import com.oriole.wisepen.system.api.enums.FeedbackType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class FeedbackListItemResponse {
    @Schema(description = "反馈 ID", requiredMode = Schema.RequiredMode.REQUIRED, type = "string")
    private Long feedbackId;

    @Schema(description = "提交用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, type = "string")
    private Long userId;

    @Schema(description = "反馈类型集合", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[\"BUG_REPORT\",\"SUGGESTION\"]")
    private Set<FeedbackType> type;

    @Schema(description = "反馈状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private FeedbackStatus status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;
}
