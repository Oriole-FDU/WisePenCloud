package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.GroupType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
public class GroupDisplayBase implements Serializable {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "小组名称")
    private String groupName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "小组描述")
    private String groupDesc;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "小组封面")
    private String groupCoverUrl;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "小组类型")
    private GroupType groupType;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "小组扩展元信息")
    private String groupMetaInfo;
}
