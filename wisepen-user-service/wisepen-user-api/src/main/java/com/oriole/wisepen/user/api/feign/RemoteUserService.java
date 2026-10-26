package com.oriole.wisepen.user.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoAdminUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileAdminUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserRegisterReqDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "remoteUserService", value = "wisepen-user-service")
public interface RemoteUserService {

    @GetMapping("/internal/user/getUserDisplayInfo")
    R<Map<Long, UserDisplayBase>> getUserDisplayInfo(@RequestParam("userId") List<Long> userIds);

    @GetMapping("/internal/group/getGroupDisplayInfo")
    R<Map<Long, GroupDisplayBase>> getGroupDisplayInfo(@RequestParam("groupId") List<Long> groupIds);

    @GetMapping("/internal/group/member/listGroupMemberIds")
    R<List<Long>> listGroupMemberIds(@RequestParam("groupId") Long groupId);

    @PostMapping("/internal/user/createUser")
    R<Long> createUser(@RequestBody UserRegisterReqDTO request);

    @PostMapping("/internal/user/changeUserInfo")
    R<Void> changeUserInfo(@RequestBody UserInfoAdminUpdateRequest request);

    @PostMapping("/internal/user/changeUserProfile")
    R<Void> changeUserProfile(@RequestBody UserProfileAdminUpdateRequest request);

}
