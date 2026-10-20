package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.ResourceGroupDashboardMetricType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class GroupResourceMetricBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "新增资源数")
    private Integer resourceAddedCount = 0;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源阅读数")
    private Integer resourceReadCount = 0;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源点赞数")
    private Integer resourceLikeCount = 0;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源评论数")
    private Integer resourceCommentCount = 0;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源 AI 调用数")
    private Integer resourceAiCallCount = 0;

    public void setMetricValue(ResourceGroupDashboardMetricType metricType, Integer value) {
        switch (metricType) {
            case RESOURCE_ADDED -> this.resourceAddedCount = value;
            case RESOURCE_READ -> this.resourceReadCount = value;
            case RESOURCE_LIKE -> this.resourceLikeCount = value;
            case RESOURCE_COMMENT -> this.resourceCommentCount = value;
            case RESOURCE_AI_CALL -> this.resourceAiCallCount = value;
        }
    }

    public void addMetricValue(ResourceGroupDashboardMetricType metricType, Integer value) {
        switch (metricType) {
            case RESOURCE_ADDED -> this.resourceAddedCount += value;
            case RESOURCE_READ -> this.resourceReadCount += value;
            case RESOURCE_LIKE -> this.resourceLikeCount += value;
            case RESOURCE_COMMENT -> this.resourceCommentCount += value;
            case RESOURCE_AI_CALL -> this.resourceAiCallCount += value;
        }
    }

    public void addMetricValue(GroupResourceMetricBase metricValue) {
        this.resourceAddedCount += metricValue.getResourceAddedCount();
        this.resourceReadCount += metricValue.getResourceReadCount();
        this.resourceLikeCount += metricValue.getResourceLikeCount();
        this.resourceCommentCount += metricValue.getResourceCommentCount();
        this.resourceAiCallCount += metricValue.getResourceAiCallCount();
    }
}
