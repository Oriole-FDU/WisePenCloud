package com.oriole.wisepen.system.service.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class BusinessConfigDefinition {
    private final String code;
    private final String name;
    private final String description;
    private final String service;
    private final String dataId;
    private final String group;
    private final List<String> path;
    private final boolean restartRequired;
    private final JsonNode schema;
    private final JsonSchema compiledSchema;
}
