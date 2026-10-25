package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupResourceMetricBase;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupResourceDailyMetricResponse extends GroupResourceMetricBase {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date", description = "统计日期")
    private LocalDate statDate;
}
