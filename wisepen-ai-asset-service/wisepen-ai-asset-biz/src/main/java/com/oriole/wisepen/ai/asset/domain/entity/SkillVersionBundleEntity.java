package com.oriole.wisepen.ai.asset.domain.entity;

import com.oriole.wisepen.ai.asset.domain.base.AssetInfoBase;
import com.oriole.wisepen.ai.asset.enums.AssetUploadStatus;
import com.oriole.wisepen.ai.asset.exception.SkillError;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@Document(collection = "wisepen_skill_versions")
public class SkillVersionBundleEntity extends BaseVersionBundleEntity {

    private static final String ROOT_PATH = "/";
    private static final String MAIN_SKILL_MD = "SKILL.md";

    // skill 发布要求核心文件 SKILL.md 存在且已上传完成
    @Override
    public void checkReadyToPublish() {
        AssetInfoBase coreAsset = getAssets() == null ? null : getAssets().stream()
                .filter(asset -> ROOT_PATH.equals(asset.getPath()) && MAIN_SKILL_MD.equals(asset.getName()))
                .findFirst().orElse(null);
        if (coreAsset == null || coreAsset.getObjectKey() == null || coreAsset.getObjectKey().isBlank()
                || coreAsset.getUploadStatus() != AssetUploadStatus.AVAILABLE) {
            throw new ServiceException(SkillError.SKILL_CORE_ASSET_NOT_FOUND);
        }
    }
}
