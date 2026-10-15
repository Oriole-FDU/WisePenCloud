package com.oriole.wisepen.extension.fudan.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusIdentityTaskResultDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.LoginChallengeDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.req.CampusIdentityInitiateRequest;
import com.oriole.wisepen.campus.extension.api.enums.CampusIdentityTaskState;
import com.oriole.wisepen.campus.extension.api.enums.LoginChallengeType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.extension.fudan.cache.RedisCacheManager;
import com.oriole.wisepen.extension.fudan.constant.FudanExtensionConstants;
import com.oriole.wisepen.extension.fudan.domain.dto.FudanUISTaskResultDTO;
import com.oriole.wisepen.extension.fudan.domain.mq.FudanUISAuthRequestMessage;
import com.oriole.wisepen.extension.fudan.exception.FudanExtensionError;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.oriole.wisepen.extension.fudan.constant.FudanExtensionConstants.UNIVERSITY_NAME;
import static com.oriole.wisepen.extension.fudan.constant.MqTopicConstants.FUDAN_UIS_AUTH_REQ;

@Slf4j
@Service
@RequiredArgsConstructor
public class FudanUISAuthTaskService {

    private final RedisCacheManager redisCacheManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 返回复旦 Provider 的能力描述，供 user-service 和前端选择认证方式
     */
    public CampusIdentityProviderDescriptorDTO getProviderDescriptor() {
        return CampusIdentityProviderDescriptorDTO.builder()
                .providerCode(FudanExtensionConstants.CAMPUS_EXTENSION_CODE)
                .displayName("复旦 UIS 认证")
                .universityName(UNIVERSITY_NAME)
                .requiredInputs(List.of("account", "password"))
                .supportedChallenges(List.of(LoginChallengeType.QR_SCAN))
                .build();
    }

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = FUDAN_UIS_AUTH_REQ,
            description = "复旦扩展服务受理 UIS 认证任务后发布请求，由消费者驱动浏览器执行认证。",
            payloadType = FudanUISAuthRequestMessage.class,
            message = @AsyncMessage(name = "FudanUISAuthRequestMessage", title = "复旦 UIS 认证请求")
    ))
    public String initiateVerifyTask(CampusIdentityInitiateRequest request) {
        if (!FudanExtensionConstants.CAMPUS_EXTENSION_CODE.equals(request.getProviderCode())) {
            throw new ServiceException(FudanExtensionError.UIS_REQUEST_INVALID, "Provider 编码不匹配");
        }

        Long userId = request.getUserId();
        String taskId = IdUtil.fastSimpleUUID();
        Map<String, Object> payload = request.getPayload();
        if (payload == null) {
            throw new ServiceException(FudanExtensionError.UIS_REQUEST_INVALID, "payload 不能为空");
        }
        String account = requireCredential(payload, "account").trim();
        String password = requireCredential(payload, "password");
        if (account.isEmpty() || password.isBlank()) {
            throw new ServiceException(FudanExtensionError.UIS_REQUEST_INVALID, "账号和密码不能为空");
        }

        FudanUISAuthRequestMessage message = FudanUISAuthRequestMessage.builder()
                .userId(userId).taskId(taskId).account(account).password(password).build();

        // 建立可供前端轮询的状态
        redisCacheManager.setUisTaskStatus(taskId, FudanUISTaskResultDTO.of(userId, taskId, CampusIdentityTaskState.QUEUED));
        redisCacheManager.setCurrentTaskId(userId, taskId);
        try {
            // 受理成功以 Kafka 确认为准
            kafkaTemplate.send(FUDAN_UIS_AUTH_REQ, objectMapper.writeValueAsString(message)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            redisCacheManager.setUisTaskStatus(taskId, FudanUISTaskResultDTO.of(userId, taskId, CampusIdentityTaskState.FAILED));
            ServiceException failure = new ServiceException(FudanExtensionError.UIS_TASK_SUBMIT_FAILED);
            failure.initCause(e);
            throw failure;
        }
        log.debug("uis auth request published. topic={} userId={}", FUDAN_UIS_AUTH_REQ, userId);
        return taskId;
    }

    private String requireCredential(Map<String, Object> payload, String fieldName) {
        Object value = payload.get(fieldName);
        if (!(value instanceof String credential)) {
            throw new ServiceException(FudanExtensionError.UIS_REQUEST_INVALID, fieldName + " 必须是字符串");
        }
        return credential;
    }

    /**
     * 读取指定复旦 UIS 任务并转换为标准校园身份认证结果。
     */
    public CampusIdentityTaskResultDTO checkVerifyTask(String taskId, Long userId) {
        if (taskId == null || taskId.isBlank()) { // 兼容旧接口
            taskId = redisCacheManager.getCurrentTaskId(userId);
        }
        var taskResult = redisCacheManager.getUisTaskStatus(taskId);
        if (taskResult == null || !userId.equals(taskResult.getUserId())) {
            throw new ServiceException(FudanExtensionError.UIS_TASK_NOT_FOUND);
        }

        LoginChallengeDTO challengeDTO = null;
        if (taskResult.getState() == CampusIdentityTaskState.CHALLENGE_REQUIRED) {
            challengeDTO = LoginChallengeDTO.builder()
                    .challengeId(IdUtil.fastSimpleUUID())
                    .type(LoginChallengeType.QR_SCAN)
                    .base64Images(List.of(taskResult.getQrBase64()))
                    .build();
        }

        return CampusIdentityTaskResultDTO.builder()
                .providerCode(FudanExtensionConstants.CAMPUS_EXTENSION_CODE)
                .userId(taskResult.getUserId())
                .taskId(taskResult.getTaskId())
                .message(taskResult.getMessage())
                .challenge(challengeDTO)
                .claims(toVerifiedIdentityClaims(taskResult.getProfile()))
                .state(taskResult.getState()).build();
    }

    /**
     * 将复旦 UIS 页面上的原始中文字段归一化为用户服务可消费的标准 claims。
     */
    private VerifiedIdentityClaimsDTO toVerifiedIdentityClaims(Map<String, String> profile) {
        if (profile == null) return null;

        boolean teacher = StrUtil.isNotBlank(readProfile(profile, "职工号"))
                || StrUtil.isNotBlank(readProfile(profile, "参加工作年月"));

        return VerifiedIdentityClaimsDTO.builder()
                .realName(readProfile(profile, "姓名"))
                .campusNo(readProfile(profile, "学号", "职工号"))
                .identityType(teacher ? IdentityType.TEACHER : IdentityType.STUDENT)
                .universityName(UNIVERSITY_NAME)
                .college(readProfile(profile, "院系", "所属院系", "院系部门"))
                .major(teacher ? null : readProfile(profile, "专业"))
                .className(teacher ? null : readProfile(profile, "班级"))
                .enrollmentYear(teacher ? null : parseYear(readProfile(profile, "年级", "入学时间")))
                .degreeLevel(parseDegreeLevel(readProfile(profile, "培养层次", "学生类别", "学历")))
                .gender(parseGender(readProfile(profile, "性别")))
                .academicTitle(teacher ? readProfile(profile, "专技职务") : null)
                .email(readProfile(profile, "电子信箱", "电子邮件", "复旦邮箱"))
                .mobile(parseMobile(readProfile(profile, "手机号码", "联系电话")))
                .build();
    }

    private String readProfile(Map<String, String> profile, String... keys) {
        if (profile == null || profile.isEmpty()) return null;
        for (String key : keys) {
            String value = profile.get(key);
            if (StrUtil.isNotBlank(value)) return value.trim();
        }
        return null;
    }

    private String parseMobile(String value) { // UIS可能存在多个手机号码
        if (StrUtil.isBlank(value)) {
            return null;
        }
        Pattern pattern = Pattern.compile("[,，、]");
        String[] items = pattern.split(value);

        return items.length > 0 ? items[0] : null;
    }

    private Integer parseYear(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d{4})").matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private DegreeLevel parseDegreeLevel(String value) {
        if (StrUtil.isBlank(value)) return DegreeLevel.UNKNOWN;
        if (value.contains("博士")) return DegreeLevel.DOCTOR;
        if (value.contains("硕士")) return DegreeLevel.MASTER;
        if (value.contains("本科")) return DegreeLevel.UNDERGRADUATE;
        return DegreeLevel.UNKNOWN;
    }

    private GenderType parseGender(String value) {
        if ("男".equals(value)) return GenderType.MALE;
        if ("女".equals(value)) return GenderType.FEMALE;
        return GenderType.UNKNOWN;
    }
}
