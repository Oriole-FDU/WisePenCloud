package com.oriole.wisepen.system.service.executor.user.register;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import com.oriole.wisepen.system.api.enums.BatchTaskType;
import com.oriole.wisepen.system.domain.entity.BatchTaskEntity;
import com.oriole.wisepen.system.domain.entity.BatchTaskItemEntity;
import com.oriole.wisepen.system.excpetion.BatchTaskError;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.service.batch.BatchTaskExecutor;
import com.oriole.wisepen.user.api.domain.base.UserRegistrationBase;
import com.oriole.wisepen.user.api.domain.dto.req.UserDefaultPasswordRegisterRequest;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserRegisterBatchTaskExecutor implements BatchTaskExecutor {

    // 用户注册涉及敏感信息，采用静态结构白名单+正则预检双重保障
    private static final Set<String> TASK_META_FIELDS = Set.of("users", "inviteCode");
    private static final Set<String> USER_INFO_FIELDS = Set.of(
            "userId",
            "username",
            "nickname",
            "avatar",
            "identityType",
            "realName",
            "campusNo",
            "email",
            "mobile",
            "verificationMode",
            "verificationProviderCode",
            "userStatus",
            "profile");
    private static final Set<String> USER_PROFILE_FIELDS = Set.of(
            "sex",
            "university",
            "college",
            "major",
            "className",
            "enrollmentYear",
            "degreeLevel",
            "academicTitle");

    private final ObjectMapper objectMapper;
    private final RemoteUserService remoteUserService;
    private final Validator validator;

    @Value("${wisepen.batch-task.user-register-max-users:1000}")
    private int maxUserRegisterUsers;

    @Override
    public BatchTaskType getType() {
        return BatchTaskType.USER_REGISTER;
    }

    @Override
    public List<BatchTaskItemEntity> expandTask(BatchTaskEntity task) {
        UserRegisterTaskMeta taskMeta;
        List<UserRegistrationBase> users;
        String inviteCode;
        try {
            if (!StringUtils.hasText(task.getMeta())) {
                throw invalidMeta("meta 不能为空");
            }

            JsonNode metaNode = objectMapper.readTree(task.getMeta());
            if (metaNode == null || !metaNode.isObject()) {
                throw invalidMeta("meta 必须是 JSON 对象");
            }
            checkFields(metaNode, TASK_META_FIELDS, "meta");

            users = expandUsers(metaNode.get("users"));

            JsonNode inviteCodeNode = metaNode.get("inviteCode");
            if (inviteCodeNode != null && !inviteCodeNode.isNull() && !inviteCodeNode.isTextual()) {
                throw invalidMeta("meta.inviteCode 必须是字符串");
            }
            inviteCode = inviteCodeNode != null && inviteCodeNode.isTextual()
                    ? inviteCodeNode.asText().trim() : null;
            if (inviteCode != null && inviteCode.isEmpty()) {
                inviteCode = null;
            }
            taskMeta = new UserRegisterTaskMeta();
            taskMeta.setUsers(users);
            taskMeta.setInviteCode(inviteCode);
        } catch (JsonProcessingException e) {
            throw invalidMeta("meta 数据无法解析，请检查字段类型和枚举值");
        }

        R<Boolean> configurationResponse = remoteUserService.isDefaultPasswordConfigured();
        if (configurationResponse == null
                || !Integer.valueOf(HttpStatus.OK.value()).equals(configurationResponse.getCode())) {
            throw new ServiceException(BatchTaskError.USER_REGISTER_DEFAULT_PASSWORD_CONFIG_INVALID, "user service默认密码配置检查失败");
        }
        if (!Boolean.TRUE.equals(configurationResponse.getData())) {
            throw new ServiceException(BatchTaskError.USER_REGISTER_DEFAULT_PASSWORD_CONFIG_INVALID, "user service未配置默认密码");
        }

        try {
            task.setMeta(objectMapper.writeValueAsString(taskMeta));

            List<BatchTaskItemEntity> items = new ArrayList<>(users.size());
            for (UserRegistrationBase user : users) {
                UserDefaultPasswordRegisterRequest itemMeta = new UserDefaultPasswordRegisterRequest();
                BeanUtil.copyProperties(user, itemMeta);
                itemMeta.setInviteCode(inviteCode);
                items.add(BatchTaskItemEntity.builder()
                        .meta(objectMapper.writeValueAsString(itemMeta))
                        .status(BatchTaskItemStatus.PENDING)
                        .build());
            }
            return items;
        } catch (JsonProcessingException e) {
            throw invalidMeta("USER_REGISTER 任务项 meta 序列化出错");
        }
    }

    @Override
    public BatchTaskItemEntity executeItem(BatchTaskItemEntity taskItem) {
        UserDefaultPasswordRegisterRequest registerRequest;
        try {
            registerRequest = objectMapper.readValue(
                    taskItem.getMeta(), UserDefaultPasswordRegisterRequest.class);

            Set<ConstraintViolation<UserDefaultPasswordRegisterRequest>> registerViolations =
                    validator.validate(registerRequest);
            if (!registerViolations.isEmpty()) {
                // 按属性路径排序，保证多条违规并存时报错稳定可复现
                String message = registerViolations.stream()
                        .sorted(Comparator.comparing(
                                (ConstraintViolation<UserDefaultPasswordRegisterRequest> violation) ->
                                        violation.getPropertyPath().toString()))
                        .map(ConstraintViolation::getMessage)
                        .findFirst()
                        .orElse("");
                throw new ServiceException(SysError.BATCH_TASK_META_INVALID, message);
            }

            R<Long> registerResponse = remoteUserService.registerWithDefaultPassword(registerRequest);
            if (registerResponse == null
                    || !Integer.valueOf(HttpStatus.OK.value()).equals(registerResponse.getCode())) {
                String remoteMessage = registerResponse == null ? "用户服务无响应" : registerResponse.getMsg();
                throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED, remoteMessage);
            }
            if (registerResponse.getData() == null) {
                throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED);
            }

            ObjectNode result = objectMapper.createObjectNode()
                    .put("userId", registerResponse.getData())
                    .put("username", registerRequest.getUsername());
            taskItem.setResult(objectMapper.writeValueAsString(result));
            return taskItem;
        } catch (JsonProcessingException e) {
            throw invalidMeta("任务项用户信息无法解析");
        }
    }

    private List<UserRegistrationBase> expandUsers(JsonNode usersNode) {
        if (usersNode == null || !usersNode.isArray() || usersNode.isEmpty()) {
            throw invalidMeta("meta.users 必须是至少包含一个用户的 JSON 数组");
        }
        if (usersNode.size() > maxUserRegisterUsers) {
            throw new ServiceException(BatchTaskError.USER_REGISTER_BATCH_SIZE_EXCEEDED);
        }

        List<UserRegistrationBase> users = new ArrayList<>(usersNode.size());
        Set<String> uniqueUsernames = new HashSet<>(usersNode.size());
        Set<Long> uniqueUserIds = new HashSet<>(usersNode.size());
        for (int index = 0; index < usersNode.size(); index++) {
            String userLocation = "users[" + index + "]";
            JsonNode userNode = usersNode.get(index);
            if (!userNode.isObject()) {
                throw invalidMeta(userLocation + " 必须是 JSON 对象");
            }
            checkFields(userNode, USER_INFO_FIELDS, userLocation);

            JsonNode profileNode = userNode.get("profile");
            if (profileNode != null && !profileNode.isNull()) {
                if (!profileNode.isObject()) {
                    throw invalidMeta(userLocation + ".profile 必须是 JSON 对象");
                }
                checkFields(profileNode, USER_PROFILE_FIELDS, userLocation + ".profile");
            }

            UserRegistrationBase user;
            try {
                user = objectMapper.treeToValue(userNode, UserRegistrationBase.class);
            } catch (JsonProcessingException e) {
                throw invalidMeta(userLocation + " 包含无法解析的字段类型或枚举值");
            }
            // 判空与归一化绑定，避免 trim 依赖上游判空
            String username = user.getUsername();
            if (!StringUtils.hasText(username)) {
                throw invalidMeta(userLocation + ".username 不能为空");
            }
            user.setUsername(username.trim());
            // 格式由 @ValidUsername 校验，此处只做跨项唯一性（executeItem 单项视角无法感知）
            if (!uniqueUsernames.add(user.getUsername())) {
                throw invalidMeta(userLocation + ".username 不能与其他用户重复");
            }
            if (user.getUserId() != null && !uniqueUserIds.add(user.getUserId())) {
                throw invalidMeta(userLocation + ".userId 不能与其他用户重复");
            }
            Set<ConstraintViolation<UserRegistrationBase>> violations = validator.validate(user);
            if (!violations.isEmpty()) {
                String message = violations.stream()
                        .sorted(Comparator.comparing(
                                (ConstraintViolation<UserRegistrationBase> violation) ->
                                        violation.getPropertyPath().toString()))
                        .map(ConstraintViolation::getMessage)
                        .findFirst()
                        .orElse("");
                throw invalidMeta(userLocation + ": " + message);
            }
            users.add(user);
        }
        return users;
    }

    private void checkFields(JsonNode objectNode, Set<String> allowedFields, String location) {
        Iterator<String> fieldNames = objectNode.fieldNames();
        while (fieldNames.hasNext()) {
            if (!allowedFields.contains(fieldNames.next())) {
                String supportedFields = allowedFields.stream()
                        .sorted()
                        .collect(Collectors.joining(", "));
                throw invalidMeta(location + " 包含不支持的字段，支持字段: " + supportedFields);
            }
        }
    }

    private ServiceException invalidMeta(String detail) {
        return new ServiceException(SysError.BATCH_TASK_META_INVALID, detail);
    }
}
