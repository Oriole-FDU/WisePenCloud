package com.oriole.wisepen.resource.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MarketResourceIndexDeleteByVersionEvent {

    private String resourceId;
    private String marketGroupId;
    private Integer offerVersion;
}
