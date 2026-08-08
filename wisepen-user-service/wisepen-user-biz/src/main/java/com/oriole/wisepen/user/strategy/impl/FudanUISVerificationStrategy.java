package com.oriole.wisepen.user.strategy.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.domain.R;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
    public VerificationResultDTO verify(Map<String, Object> payload) {
        Long userId = (Long) payload.get("userId");
        R<FudanUISTaskResultDTO> res = remoteFudanExtensionService.getTaskStatus(userId);
        if (res.getCode() != 200 || res.getData() == null) {
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

        UserProfileEntity userProfileEntity = new UserProfileEntity();
        UserEntity userEntity = new UserEntity();

        String campusNo = profile.get("学号");
        long existed = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getCampusNo, campusNo)
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                .ne(UserEntity::getUserId, userId));
        if (existed > 0) {
            log.warn("fudan uis verify rejected for bound campus number. userId={} campusNo={}",
                    userId, campusNo);
            throw new ServiceException(UserError.VERIFICATION_CAMPUS_NO_ALREADY_EXISTS);
        }

        // 设置学号、真实姓名、手机号、邮箱
        userEntity.setCampusNo(campusNo);
        userEntity.setRealName(profile.get("姓名"));
        String mobile = profile.get("手机号码");
        if (StrUtil.isNotBlank(mobile)) {
            userEntity.setMobile(mobile);
        }
        String email = profile.get("电子信箱");
        if (StrUtil.isNotBlank(email)) {
            userEntity.setEmail(email);
        }
        userEntity.setUserStatus(UserStatus.NORMAL);
        userEntity.setVerificationMode(UserVerificationMode.FDU_UIS_SYS);

        // 设置性别、院系、专业、年级、培养层次等信息
        String sexStr = profile.get("性别");
        userProfileEntity.setSex(
                StrUtil.isBlank(sexStr) ? GenderType.UNKNOWN :
                "男".equals(sexStr) ? GenderType.MALE :
                "女".equals(sexStr) ?  GenderType.FEMALE :
                GenderType.UNKNOWN
        );
        userProfileEntity.setUniversity("复旦大学"); // 复旦大学UIS认证固定值
        userProfileEntity.setCollege(profile.get("院系"));
        userProfileEntity.setMajor(profile.get("专业"));
        String gradeStr = profile.get("年级");
        userProfileEntity.setEnrollmentYear(StrUtil.isBlank(gradeStr)? null : Integer.parseInt(gradeStr));
        String degreeStr = profile.get("培养层次");
        userProfileEntity.setDegreeLevel(
                StrUtil.isBlank(degreeStr) ? DegreeLevel.UNKNOWN :
                        degreeStr.contains("博士") ? DegreeLevel.DOCTOR :
                        degreeStr.contains("硕士") ? DegreeLevel.MASTER :
                        degreeStr.contains("本科") ? DegreeLevel.UNDERGRADUATE :
                        DegreeLevel.UNKNOWN
        );

        userEntity.setUserId(userId);
        userMapper.updateById(userEntity);
        userProfileEntity.setUserId(userId);
        userProfileMapper.updateById(userProfileEntity);

        redisCacheManager.updateUserStatusInSession(userId, UserStatus.NORMAL);
        log.info("fudan uis verify succeeded. userId={} campusNo={}", userId, campusNo);
        return VerificationResultDTO.success();
    }

}
