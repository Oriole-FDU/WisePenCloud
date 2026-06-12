package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.user.api.domain.dto.res.UserSearchResponse;

import java.util.List;

public interface ISearchUserService {

    // 在指定小组范围内搜索用户展示信息；keyword 为空时返回范围内全部成员
    PageR<UserSearchResponse> searchUsers(String keyword, List<Long> groupIds, int page, int size);
}
