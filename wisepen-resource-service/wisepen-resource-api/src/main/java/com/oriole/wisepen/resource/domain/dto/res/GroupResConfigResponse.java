package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupResConfigResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "小组 ID")
    private String groupId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "文件组织方式")
    private FileOrganizationLogic fileOrgLogic;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "普通成员默认权限")
    private List<ResourceAction> defaultMemberActions = new ArrayList<>();
}
