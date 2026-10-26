package com.oriole.wisepen.system.service.executor.user.register;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import com.oriole.wisepen.system.api.enums.BatchTaskType;
import com.oriole.wisepen.system.domain.entity.BatchTaskEntity;
import com.oriole.wisepen.system.domain.entity.BatchTaskItemEntity;
import com.oriole.wisepen.system.excpetion.BatchTaskError;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.service.batch.BatchTaskExecutor;
import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoAdminUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileAdminUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.UserRegisterReqDTO;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserRegisterBatchTaskExecutor implements BatchTaskExecutor {

    private final ObjectMapper objectMapper;
    private final RemoteUserService remoteUserService;

    @Value("${wisepen.batch-task.user-register-max-users:1000}")
    private int maxUserRegisterUsers;

    @Override
    public BatchTaskType getType() {
        return BatchTaskType.USER_REGISTER;
    }

    @Override
    public List<BatchTaskItemEntity> expandTask(BatchTaskEntity task) {
        UserRegisterTaskMeta taskMeta;
        try {
            taskMeta = objectMapper.readValue(task.getMeta(), UserRegisterTaskMeta.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID);
        }

        if (taskMeta.getUsers() == null || taskMeta.getUsers().isEmpty()) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID);
        }
        if (taskMeta.getUsers().size() > maxUserRegisterUsers) {
            throw new ServiceException(BatchTaskError.USER_REGISTER_BATCH_SIZE_EXCEEDED);
        }

        try {
            task.setMeta(objectMapper.writeValueAsString(taskMeta));

            List<BatchTaskItemEntity> items = new ArrayList<>(taskMeta.getUsers().size());
            for (UserRegisterTaskMeta.UserMeta user : taskMeta.getUsers()) {
                UserRegisterTaskMeta itemMeta = new UserRegisterTaskMeta();
                itemMeta.setUsers(List.of(user));
                items.add(BatchTaskItemEntity.builder()
                        .meta(objectMapper.writeValueAsString(itemMeta))
                        .status(BatchTaskItemStatus.PENDING)
                        .build());
            }
            return items;
        } catch (JsonProcessingException e) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID);
        }
    }

    @Override
    public BatchTaskItemEntity executeItem(BatchTaskItemEntity taskItem) {
        try {
            UserRegisterTaskMeta taskMeta = objectMapper.readValue(taskItem.getMeta(), UserRegisterTaskMeta.class);

            if (taskMeta.getUsers() == null || taskMeta.getUsers().size() != 1) {
                throw new ServiceException(SysError.BATCH_TASK_META_INVALID);
            }

            UserRegisterTaskMeta.UserMeta user = taskMeta.getUsers().getFirst();
            UserRegisterReqDTO registerRequest;
            try {
                registerRequest = objectMapper.convertValue(user, UserRegisterReqDTO.class);
            } catch (IllegalArgumentException e) {
                throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED, e.getMessage());
            }

            R<Long> createResponse = remoteUserService.createUser(registerRequest);
            ensureRemoteSuccess(createResponse);
            Long userId = createResponse.getData();
            if (userId == null) {
                throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED);
            }

            UserInfoAdminUpdateRequest userInfoUpdate =
                    BeanUtil.copyProperties(registerRequest, UserInfoAdminUpdateRequest.class);
            userInfoUpdate.setUserId(userId);
            ensureRemoteSuccess(remoteUserService.changeUserInfo(userInfoUpdate));

            if (registerRequest.getProfile() != null) {
                UserProfileAdminUpdateRequest profileUpdate =
                        BeanUtil.copyProperties(registerRequest.getProfile(), UserProfileAdminUpdateRequest.class);
                profileUpdate.setUserId(userId);
                ensureRemoteSuccess(remoteUserService.changeUserProfile(profileUpdate));
            }

            ObjectNode result = objectMapper.createObjectNode()
                    .put("userId", userId)
                    .put("username", user.getUsername());
            taskItem.setResult(objectMapper.writeValueAsString(result));
            return taskItem;
        } catch (JsonProcessingException e) {
            throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED);
        }
    }

    private void ensureRemoteSuccess(R<?> response) {
        if (response == null || !Integer.valueOf(HttpStatus.OK.value()).equals(response.getCode())) {
            String errorMessage = response == null ? null : response.getMsg();
            throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED, errorMessage);
        }
    }
}
