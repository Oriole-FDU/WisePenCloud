package com.oriole.wisepen.resource.event;

import com.oriole.wisepen.resource.enums.UpsertField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.EnumSet;

@Data
@AllArgsConstructor
public class ResourceMetadataIndexUpsertEvent {

    private String resourceId;
    private EnumSet<UpsertField> fields;
}
