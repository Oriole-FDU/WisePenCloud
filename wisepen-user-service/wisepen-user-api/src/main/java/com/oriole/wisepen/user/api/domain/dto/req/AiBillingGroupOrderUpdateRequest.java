package com.oriole.wisepen.user.api.domain.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AiBillingGroupOrderUpdateRequest {

    @NotNull
    private List<@NotNull Long> orderedGroupIds;
}
