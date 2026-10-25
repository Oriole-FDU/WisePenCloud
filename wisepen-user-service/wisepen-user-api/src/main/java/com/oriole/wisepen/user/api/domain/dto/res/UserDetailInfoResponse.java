package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.UserInfoBase;
import com.oriole.wisepen.user.api.domain.base.UserProfileBase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserDetailInfoResponse implements Serializable {
    /** 当前用户 ID，与 userInfo 平级返回，便于前端识别登录用户 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", description = "当前用户 ID")
    private Long userId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户账号信息")
    UserInfoBase userInfo;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户资料信息")
    UserProfileBase userProfile;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "只读字段列表")
    List<String> readonlyFields = new ArrayList<>();
}
