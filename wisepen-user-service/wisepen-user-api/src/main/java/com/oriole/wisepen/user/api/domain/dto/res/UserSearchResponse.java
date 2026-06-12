package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserSearchResponse extends UserDisplayBase {
    private Long userId; // 用户 UID
}
