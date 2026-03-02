package com.oriole.wisepen.resource.domain;

import com.oriole.wisepen.resource.enums.VisibilityModeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAcl {
    private String groupId;
    private VisibilityModeEnum visibilityMode;
    private List<String> specifiedUsers;
}