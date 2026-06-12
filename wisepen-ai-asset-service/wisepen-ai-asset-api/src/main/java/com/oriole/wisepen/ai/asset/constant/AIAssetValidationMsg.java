package com.oriole.wisepen.ai.asset.constant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public interface AIAssetValidationMsg {
    String SKILL_ID_NOT_BLANK = "Skill ID 不能为空";
    String SKILL_TITLE_NOT_BLANK = "Skill 标题不能为空";
    String SKILL_VERSION_NOT_BLANK = "Skill 版本不能为空";
    String SKILL_ASSET_NAME_NOT_BLANK = "Skill 资源名不能为空";
    String SKILL_ASSET_PATH_NOT_BLANK = "Skill 资源路径不能为空";
    String SKILL_ASSET_TYPE_NOT_BLANK = "Skill 资源类型不能为空";
    String SKILL_ASSET_LIST_NOT_EMPTY = "Skill 资源列表不能为空";
}
