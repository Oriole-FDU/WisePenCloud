package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.resource.domain.dto.req.SearchQueryRequest;
import com.oriole.wisepen.resource.domain.dto.res.SearchHitItemResponse;
import com.oriole.wisepen.resource.service.ISearchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "全文搜索", description = "面向前端的全文搜索接口")
@RestController
@RequestMapping("/resource/search")
@RequiredArgsConstructor
@CheckLogin
public class SearchController {

    private final ISearchQueryService searchQueryService;

    @Operation(summary = "全局全文搜索",
            description = "按 scope (ALL/DOCUMENT/NOTE) 三 Tab 过滤，按 page/size 滚动加载")
    @Log(title = "全文搜索",
            businessType = BusinessType.SELECT,
            isSaveRequestData = false,
            isSaveResponseData = false)
    @PostMapping("/global")
    public R<PageResult<SearchHitItemResponse>> globalSearch(@Validated @RequestBody SearchQueryRequest reqDTO) {
        return R.ok(searchQueryService.globalSearch(reqDTO));
    }
}
