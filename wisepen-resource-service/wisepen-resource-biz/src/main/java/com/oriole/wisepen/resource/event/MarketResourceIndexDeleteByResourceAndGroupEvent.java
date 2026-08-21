package com.oriole.wisepen.resource.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MarketResourceIndexDeleteByResourceAndGroupEvent {

    private String resourceId;
    private String marketGroupId;
}
