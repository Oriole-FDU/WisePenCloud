package com.oriole.wisepen.resource.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupTagBind {
    private String groupId;

    // 该组下绑定的 Tag 列表
    // 保序，索引为 0 的元素即为该组的主标签
    @Indexed // 加上索引，方便后续 Tag 权限变更时反查关联了该 Tag 的资源
    private List<String> tagIds;
}