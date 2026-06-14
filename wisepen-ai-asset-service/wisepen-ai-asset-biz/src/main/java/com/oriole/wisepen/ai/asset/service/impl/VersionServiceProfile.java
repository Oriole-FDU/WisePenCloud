package com.oriole.wisepen.ai.asset.service.impl;

import com.oriole.wisepen.ai.asset.domain.entity.BaseVersionBundleEntity;
import com.oriole.wisepen.ai.asset.repository.BaseVersionBundleRepository;
import com.oriole.wisepen.common.core.domain.IResult;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import lombok.Builder;
import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 同一份 VersionServiceImpl 在装配为 skill / agent 两个 Bean 时的差异集合
 */
@Getter
@Builder
public class VersionServiceProfile<T extends BaseVersionBundleEntity> {

    private final StorageSceneEnum scene;

    private final String logTag;

    private final BaseVersionBundleRepository<T> repository;

    // 新建空草稿实体
    private final Supplier<T> draftFactory;

    // 读 skill / agent 主档当前发布版本号，资源缺失时抛各自的 NOT_FOUND
    private final Function<String, Integer> publishedVersionLoader;

    // 发布时把主档当前版本号更新为新版本
    private final BiConsumer<String, Integer> publishedVersionUpdater;

    private final IResult versionNotFound;

    private final IResult nonDraft;

    private final IResult pathInvalid;

    private final IResult uploadApplyFailed;

    private final IResult assetNotReady;
}
