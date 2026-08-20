package com.oriole.wisepen.ai.asset.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.domain.dto.req.AIResourceMetaInfoListRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.AIResourceMetaInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillVersionBundleInfoResponse;
import com.oriole.wisepen.ai.asset.domain.entity.SkillVersionBundleEntity;
import com.oriole.wisepen.ai.asset.exception.AIResourceError;
import com.oriole.wisepen.ai.asset.service.impl.SkillServiceImpl;
import com.oriole.wisepen.ai.asset.service.impl.SkillVersionServiceImpl;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;

import java.util.List;

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

    @GetMapping("/getSkillByResourceId")
    public R<SkillInfoResponse> getSkillByResourceId(@RequestParam String resourceId, @RequestParam(required = false) Integer skillVersion) {
        AIResourceMetaInfoResponse skill = skillService.getAIResourceInfo(resourceId);
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
        return R.ok(skillService.listPublishedAIResourcesMeta(request == null ? null : request.getResourceIds()));
    }
}
