package com.oriole.wisepen.resource.domain.base;

import com.oriole.wisepen.resource.enums.AccessControlScope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TagInfoBase extends TagSpaceBase{
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "标签名称")
    private String tagName;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "标签描述")
    private String tagDesc;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "标签图标")
    private String tagIcon;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "标签颜色")
    private String tagColor;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "标签元信息")
    private String tagMetaInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, description = "创建者用户 ID")
    private String tagCreator; // 创建者 userId，系统自动创建的路径节点为空

    // 节点类型标识：true 表示 FOLDER(路径)，false 表示普通 TAG
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "是否为路径节点")
    private Boolean isPath;
}
