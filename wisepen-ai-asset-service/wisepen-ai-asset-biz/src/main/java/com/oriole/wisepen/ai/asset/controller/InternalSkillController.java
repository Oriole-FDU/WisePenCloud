package com.oriole.wisepen.ai.asset.controller;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.ai.asset.domain.base.SkillInfoBase;
import com.oriole.wisepen.ai.asset.domain.dto.req.SkillMetaInfoListRequest;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillInfoResponse;
import com.oriole.wisepen.ai.asset.domain.dto.res.SkillMetaInfoResponse;
import com.oriole.wisepen.ai.asset.exception.SkillError;
import com.oriole.wisepen.ai.asset.service.ISkillService;
import com.oriole.wisepen.ai.asset.service.IVersionService;
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

    private final ISkillService skillService;
    private final IVersionService skillVersionService;

    @GetMapping("/getSkillByResourceId")
    public R<SkillInfoResponse> getPublishedSkillByResourceId(@RequestParam String resourceId, @RequestParam(required = false) Integer skillVersion) {
        SkillInfoBase skill = skillService.getSkillInfo(resourceId);
        if (skillVersion == null) skillVersion = skill.getVersion();
        if (skillVersion <= 0) {
            throw new ServiceException(SkillError.SKILL_VERSION_NOT_FOUND);
        }
        SkillInfoResponse response = BeanUtil.copyProperties(skill, SkillInfoResponse.class);
        response.setSkillVersionBundle(skillVersionService.getSkillVersionBundle(resourceId, skillVersion));
        return R.ok(response);
    }

    @PostMapping("/listPublishedSkillsMetaByResourceIds")
    public R<List<SkillMetaInfoResponse>> listPublishedSkillMetasByResourceIds(@RequestBody SkillMetaInfoListRequest request) {
        return R.ok(skillService.listPublishedSkillsMeta(request == null ? null : request.getResourceIds()));
    }
}
