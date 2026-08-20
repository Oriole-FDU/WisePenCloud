package com.oriole.wisepen.user.strategy.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.extension.fudan.domain.dto.FudanUISTaskResultDTO;
import com.oriole.wisepen.extension.fudan.domain.mq.FudanUISAuthRequestMessage;
import com.oriole.wisepen.extension.fudan.enums.FudanUISTaskState;
import com.oriole.wisepen.extension.fudan.feign.RemoteFudanExtensionService;
import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.enums.DegreeLevel;
import com.oriole.wisepen.user.api.enums.GenderType;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.mq.KafkaUserEventPublisher;
import com.oriole.wisepen.user.strategy.UserVerificationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class FudanUISVerificationStrategy implements UserVerificationStrategy {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final RemoteFudanExtensionService remoteFudanExtensionService;
    private final KafkaUserEventPublisher kafkaUserEventPublisher;
    private final RedisCacheManager redisCacheManager;

    @Override
    public UserVerificationMode getMode() {
        return UserVerificationMode.FDU_UIS_SYS;
    }

    @Override
    public List<String> getReadonlyFields() {
        return Arrays.asList(
                "username", "realName", "campusNo", "email", "mobile", "userStatus",
                "sex", "university", "college", "major",
                "className", "enrollmentYear", "degreeLevel"
        );
    }

    @Override
    public void initiate(Long userId, Map<String, Object> payload) {
        UserEntity currentUser = userMapper.selectById(userId);
        validateFudanUISVerificationState(currentUser, userId);

        String uisAccount = (String) payload.get("uisAccount");
        String uisPassword = (String) payload.get("uisPassword");

        FudanUISAuthRequestMessage message = FudanUISAuthRequestMessage.builder()
                .userId(userId)
                .account(uisAccount)
                .password(uisPassword)
                .build();
        kafkaUserEventPublisher.publishUisAuthRequest(userId, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VerificationResultDTO verify(Map<String, Object> payload) {
        Long userId = (Long) payload.get("userId");
        UserEntity currentUser = userMapper.selectById(userId);
        validateFudanUISVerificationState(currentUser, userId);

        R<FudanUISTaskResultDTO> res = remoteFudanExtensionService.getTaskStatus(userId);
        if (res == null) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, "UIS 认证服务无响应");
        }
        if (!Integer.valueOf(200).equals(res.getCode()) || res.getData() == null) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, res.getMsg());
        }
        FudanUISTaskResultDTO dto = res.getData();

        if (dto.getState() == FudanUISTaskState.PENDING.getCode()) {
            return VerificationResultDTO.pending();
        }

        if (dto.getState() < 0) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, dto.getMessage());
        }

        if (dto.getState() == FudanUISTaskState.WAITING_SCAN.getCode() && dto.getQrBase64() != null) {
            return VerificationResultDTO.builder().completed(false).requireAction(true).actionPayload(dto.getQrBase64()).build();
        }

        Map<String, String> profile = dto.getProfile();
        if (profile == null || profile.isEmpty()) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, "UIS 认证资料缺失");
        }

        UserProfileEntity userProfileEntity = new UserProfileEntity();
        UserEntity userEntity = new UserEntity();

        String campusNo = getProfileValue(profile, "学号", "职工号");
        String realName = getProfileValue(profile, "姓名");
        if (StrUtil.isBlank(campusNo) || StrUtil.isBlank(realName)) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, "UIS 认证资料缺少学工号或姓名");
        }

        long existed = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getCampusNo, campusNo)
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                .ne(UserEntity::getUserId, userId));
        if (existed > 0) {
            log.warn("fudan uis verify rejected for bound campus number. userId={} campusNo={}.",
                    userId, campusNo);
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS);
        }

        boolean teacherProfile = StrUtil.isNotBlank(getProfileValue(profile, "职工号", "参加工作年月"));
        if (teacherProfile) {
            userEntity.setIdentityType(IdentityType.TEACHER);
        }

        userEntity.setCampusNo(campusNo);
        userEntity.setRealName(realName);
        String mobile = getProfileValue(profile, "手机号码", "联系电话");
        if (StrUtil.isNotBlank(mobile)) {
            userEntity.setMobile(mobile);
        }
        String email = getProfileValue(profile, "电子信箱", "电子邮件", "复旦邮箱");
        if (StrUtil.isNotBlank(email)) {
            userEntity.setEmail(email);
        }
        userEntity.setUserStatus(UserStatus.NORMAL);
        userEntity.setVerificationMode(UserVerificationMode.FDU_UIS_SYS);

        String sexStr = getProfileValue(profile, "性别");
        userProfileEntity.setSex(
                StrUtil.isBlank(sexStr) ? GenderType.UNKNOWN :
                        "男".equals(sexStr) ? GenderType.MALE :
                        "女".equals(sexStr) ? GenderType.FEMALE :
                        GenderType.UNKNOWN
        );
        userProfileEntity.setUniversity("复旦大学"); // 复旦大学UIS认证固定值
        userProfileEntity.setCollege(getProfileValue(profile, "院系", "所属院系", "院系部门"));
        userProfileEntity.setMajor(getProfileValue(profile, "专业"));
        userProfileEntity.setClassName(getProfileValue(profile, "班级"));
        String gradeStr = getProfileValue(profile, "年级", "入学时间");
        if (StrUtil.isNotBlank(gradeStr)) {
            Matcher matcher = Pattern.compile("(\\d{4})").matcher(gradeStr);
            if (matcher.find()) {
                userProfileEntity.setEnrollmentYear(Integer.parseInt(matcher.group(1)));
            }
        }
        String degreeStr = getProfileValue(profile, "培养层次", "学生类别", "学历");
        userProfileEntity.setDegreeLevel(
                StrUtil.isBlank(degreeStr) ? DegreeLevel.UNKNOWN :
                        degreeStr.contains("博士") ? DegreeLevel.DOCTOR :
                        degreeStr.contains("硕士") ? DegreeLevel.MASTER :
                        degreeStr.contains("本科") ? DegreeLevel.UNDERGRADUATE :
                        DegreeLevel.UNKNOWN
        );

        userEntity.setUserId(userId);
        if (userMapper.updateById(userEntity) != 1) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, "用户认证状态更新失败");
        }
        userProfileEntity.setUserId(userId);
        if (userProfileMapper.updateById(userProfileEntity) != 1) {
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_FAILED, "用户认证资料更新失败");
        }

        redisCacheManager.updateUserStatusInSession(userId, UserStatus.NORMAL);
        if (teacherProfile) {
            redisCacheManager.updateUserIdentityTypeInSession(userId, IdentityType.TEACHER);
        }
        log.info("fudan uis verify succeeded. userId={} campusNo={} teacherProfile={}",
                userId, campusNo, teacherProfile);
        return VerificationResultDTO.success();
    }

    private void validateFudanUISVerificationState(UserEntity userEntity, Long userId) {
        if (userEntity == null
                || userEntity.getUserStatus() != UserStatus.UNIDENTIFIED
                || !IdentityType.STUDENT.equals(userEntity.getIdentityType())
                || userEntity.getVerificationMode() != null) {
            log.warn("fudan uis verification skipped. userId={} reason=\"user state invalid\"", userId);
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_STATE_INVALID);
        }
    }

    private String getProfileValue(Map<String, String> profile, String... keys) {
        if (profile == null || profile.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            String value = profile.get(key);
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

}
