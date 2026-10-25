package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ResourceItemResponse extends ResourceBaseInfoResponse {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "资源标签绑定列表")
    @Builder.Default
    private List<ResourceTagBindResponse> tagBinds = new ArrayList<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前用户可执行动作")
    @Builder.Default
    private List<ResourceAction> currentActions = new ArrayList<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前用户对该资源的访问角色")
    private ResourceAccessRole resourceAccessRole;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "当前用户的资源互动记录")
    private ResourceUserInteractionRecordResponse myInteractionRecord;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "按小组授予的权限；仅所有者可见，非所有者返回空数组")
    @Builder.Default
    private List<GroupGrantedActionsResponse> overrideGrantedActions = new ArrayList<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "按用户授予的权限；仅所有者可见，非所有者返回空数组")
    @Builder.Default
    private List<SpecifiedUserGrantedActionsResponse> specifiedUsersGrantedActions = new ArrayList<>();

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "按集市小组索引的售卖信息")
    @Builder.Default
    private Map<String, MarketSaleInfoResponse> marketSaleInfos = new HashMap<>();

    @Builder
    @Data
    public static class GroupGrantedActionsResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "小组 ID")
        private String groupId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "小组展示信息")
        private GroupDisplayBase groupInfo;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "该小组被授予的动作")
        @Builder.Default
        private List<ResourceAction> grantedActions = new ArrayList<>();
    }

    @Builder
    @Data
    public static class SpecifiedUserGrantedActionsResponse {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户 ID")
        private String userId;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "用户展示信息")
        private UserDisplayBase userInfo;

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "该用户被授予的动作")
        @Builder.Default
        private List<ResourceAction> grantedActions = new ArrayList<>();
    }
}
