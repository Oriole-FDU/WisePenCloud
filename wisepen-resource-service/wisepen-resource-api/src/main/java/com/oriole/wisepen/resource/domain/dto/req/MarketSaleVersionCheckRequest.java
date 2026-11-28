package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketSaleVersionCheckRequest {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;

    @NotBlank(message = ResourceValidationMsg.GROUP_ID_NOT_BLANK)
    private String marketGroupId;

    @NotNull(message = ResourceValidationMsg.MARKET_SALE_VERSION_NOT_NULL)
    @Min(value = 1, message = ResourceValidationMsg.MARKET_SALE_VERSION_INVALID)
    private Integer offerVersion;
}
