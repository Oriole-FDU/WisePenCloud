package com.oriole.wisepen.user.campus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.mq.UserTaskCompleteMessage;
import com.oriole.wisepen.user.api.enums.UserTaskCode;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mq.KafkaUserEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 校园身份 claims 的统一落库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityClaimsApplier {

    private final UserMapper userMapper;
    private final KafkaUserEventPublisher kafkaUserEventPublisher;
    private final RedisCacheManager redisCacheManager;

    /** 断言学工号未被其他正常账号占用；excludeUserId 用于认证场景排除当前用户自身。 */
    public void assertCampusNoAvailable(String campusNo, Long excludeUserId, UserError error) {
        LambdaQueryWrapper<UserEntity> wrapper = Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getCampusNo, campusNo)
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL);
        if (excludeUserId != null) {
            wrapper.ne(UserEntity::getUserId, excludeUserId);
        }
        if (userMapper.selectCount(wrapper) > 0) {
            log.warn("campus number uniqueness rejected. campusNo={} excludeUserId={}", campusNo, excludeUserId);
            throw new ServiceException(error);
        }
    }

    /** 解析标准身份类型，缺失时默认学生。 */
    public IdentityType resolveIdentityType(VerifiedIdentityClaimsDTO claims) {
        return claims.getIdentityType() == null ? IdentityType.STUDENT : claims.getIdentityType();
    }

    /** 把归一化 claims 映射为账号实体公共字段；username/password 等由调用方补齐。 */
    public UserEntity buildVerifiedUserEntity(Long userId, String providerCode, VerifiedIdentityClaimsDTO claims) {
        return UserEntity.builder()
                .userId(userId)
                .identityType(resolveIdentityType(claims))
                .campusNo(claims.getCampusNo())
                .realName(claims.getRealName())
                .mobile(claims.getMobile())
                .email(claims.getEmail())
                .userStatus(UserStatus.NORMAL)
                .verificationMode(UserVerificationMode.CAMPUS_IDENTITY_PROVIDER)
                .verificationProviderCode(providerCode)
                .build();
    }

    /** 认证成功后按身份类型发布奖励并同步 context 中的身份类型。 */
    public void publishVerificationReward(Long userId, IdentityType identityType) {
        if (identityType == IdentityType.TEACHER) {
            redisCacheManager.updateUserIdentityTypeInContext(userId, IdentityType.TEACHER);
            kafkaUserEventPublisher.publishUserTaskComplete(UserTaskCompleteMessage.builder()
                    .userId(userId)
                    .taskCode(UserTaskCode.TEACHER_VERIFICATION)
                    .operatorId(userId)
                    .meta("校园教师认证赠送")
                    .build());
        } else {
            kafkaUserEventPublisher.publishUserTaskComplete(UserTaskCompleteMessage.builder()
                    .userId(userId)
                    .taskCode(UserTaskCode.STUDENT_VERIFICATION)
                    .operatorId(userId)
                    .meta("校园学生认证赠送")
                    .build());
        }
    }
}
