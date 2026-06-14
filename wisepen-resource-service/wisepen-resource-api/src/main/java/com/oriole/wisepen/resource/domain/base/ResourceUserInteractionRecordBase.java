package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUserInteractionRecordBase {
    private Boolean read = false;   // 用户是否阅读过该资源
    private Boolean liked = false;  // 用户是否赞过该资源
    private Integer score;  // 用户对该资源的评分
}
