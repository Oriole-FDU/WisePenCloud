package com.oriole.wisepen.ai.asset.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.builtin.BuiltinSkillCatalog;
import com.oriole.wisepen.ai.asset.domain.base.AIResourceInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.AIResourceMetaInfoListRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.AIResourceMetaInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.domain.entity.SkillVersionBundleEntity;
import com.oriole.wisepen.ai.asset.exception.AIResourceError;
import com.oriole.wisepen.ai.asset.service.IAIResourceService;
import com.oriole.wisepen.ai.asset.service.impl.SkillServiceImpl;
import com.oriole.wisepen.ai.asset.service.impl.SkillVersionServiceImpl;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/skill")
@RequiredArgsConstructor
public class InternalSkillController {

    private final SkillServiceImpl skillService;
    private final SkillVersionServiceImpl skillVersionService;
    private final BuiltinSkillCatalog builtinSkillCatalog;

    @GetMapping("/getSkillByResourceId")
    public R<SkillInfoResponse> getSkillByResourceId(@RequestParam String resourceId, @RequestParam(required = false) Integer skillVersion) {
        if (skillVersion == null || skillVersion == 1) {
            SkillInfoResponse builtinSkill = builtinSkillCatalog.findSkill(resourceId).orElse(null);
            if (builtinSkill != null) {
                return R.ok(builtinSkill);
            }
        }
        AIResourceInfoBase skill = skillService.getAIResourceInfo(resourceId);
        if (skillVersion == null) skillVersion = skill.getVersion();
        if (skillVersion <= 0) {
            throw new ServiceException(AIResourceError.AI_RESOURCE_VERSION_NOT_FOUND);
        }
        SkillInfoResponse response = BeanUtil.copyProperties(skill, SkillInfoResponse.class);
        SkillVersionBundleEntity bundle = skillVersionService.getVersionBundle(resourceId, skillVersion);
        response.setSkillVersionBundle(BeanUtil.copyProperties(bundle, SkillVersionBundleInfoResponse.class));
        return R.ok(response);
    }

    @PostMapping("/listPublishedSkillsMetaByResourceIds")
    public R<List<AIResourceMetaInfoResponse>> listPublishedSkillMetasByResourceIds(@RequestBody AIResourceMetaInfoListRequest request) {
        List<String> resourceIds = request == null ? null : request.getResourceIds();
        Map<String, AIResourceMetaInfoResponse> merged = new LinkedHashMap<>();
        for (AIResourceMetaInfoResponse meta : builtinSkillCatalog.listMetas(resourceIds)) {
            merged.put(meta.getResourceId(), meta);
        }
        List<String> persistedResourceIds = resourceIds == null
                ? null
                : resourceIds.stream()
                .filter(Objects::nonNull)
                .filter(resourceId -> !BuiltinSkillCatalog.NOTE_AI_DIFF_SKILL_ID.equals(resourceId))
                .toList();
        if (persistedResourceIds != null && persistedResourceIds.isEmpty()) {
            return R.ok(new ArrayList<>(merged.values()));
        }
        for (AIResourceMetaInfoResponse meta : skillService.listPublishedAIResourcesMeta(persistedResourceIds)) {
            merged.putIfAbsent(meta.getResourceId(), meta);
        }
        return R.ok(new ArrayList<>(merged.values()));
    }
}
