package com.oriole.wisepen.resource.domain;

import com.oriole.wisepen.resource.domain.entity.TagEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupTagBind {
    private String groupId;

    // 该组下绑定的 Tag 列表
    // 保序，索引为 0 的元素即为该组的主标签
    @Indexed // 加上索引，方便后续 Tag 权限变更时反查关联了该 Tag 的资源
    private List<String> tagIds;

    // 仅集市组使用
    private MarketSaleInfo marketSaleInfo;

    // 当前小组是否已审批使用组钱包支付该资源的调用费用
    private Boolean aiBillingApproved = false;

    @Builder
    public GroupTagBind(List<String> tagIds, String groupId) {
        this.tagIds = tagIds;
        this.groupId = groupId;
    }

    // 该组下绑定的 Tag 列表（详细信息列表）
    private List<TagEntity> tags = null; // 需根据 tagIds 主动查询后填入才不为空
}