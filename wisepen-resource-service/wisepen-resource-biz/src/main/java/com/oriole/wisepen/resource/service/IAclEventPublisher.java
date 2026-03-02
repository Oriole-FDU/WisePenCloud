package com.oriole.wisepen.resource.service;

public interface IAclEventPublisher {
    void publishRecalculateEvent(String resourceId, String triggerSource);
}
