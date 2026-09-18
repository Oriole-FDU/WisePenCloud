package com.oriole.wisepen.user.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserTaskType {
    ONCE("ONCE"),
    PERIODIC("PERIODIC"),
    UNCHECKED("UNCHECKED");

    @JsonValue
    private final String value;
}
