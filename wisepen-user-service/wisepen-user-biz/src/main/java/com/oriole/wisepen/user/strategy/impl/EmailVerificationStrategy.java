package com.oriole.wisepen.user.strategy.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.MailSendDTO;
import com.oriole.wisepen.system.api.feign.RemoteMailService;
import com.oriole.wisepen.user.api.config.UserProperties;
import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.strategy.email.EducationEmailSchool;
import com.oriole.wisepen.user.strategy.email.EducationEmailSchoolResolver;
import com.oriole.wisepen.user.strategy.UserVerificationStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationStrategy implements UserVerificationStrategy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");
    private static final Set<String> PARTNER_UNIVERSITIES_EMAIL_DOMAINS = Set.of("fudan.edu.cn", "shmu.edu.cn", "m.fudan.edu.cn");


    private final RedisCacheManager redisCacheManager;
    private final RemoteMailService remoteMailService;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserProperties userProperties;
    private final EducationEmailSchoolResolver educationEmailSchoolResolver;

    private final TemplateEngine templateEngine;

    @Override
    public UserVerificationMode getMode() {
        return UserVerificationMode.EDU_EMAIL; // 策略标识
    }

    @Override
    public void initiate(Long userId, Map<String, Object> payload) {
        String email = StrUtil.blankToDefault((String) payload.get("email"), "").trim().toLowerCase(Locale.ROOT);

        if (StrUtil.isBlank(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            log.warn("email verification skipped. email={} userId={} reason=\"invalid email format\"", email, userId);
            throw new ServiceException(UserError.VERIFICATION_EMAIL_INVALID);
        }
        rejectPartnerUniversitiesEmail(email, userId);

        educationEmailSchoolResolver.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("email verification skipped. email={} userId={} reason=\"unsupported education email domain\"", email, userId);
                    return new ServiceException(UserError.VERIFICATION_EMAIL_INVALID);
                });

        UserEntity currentUser = userMapper.selectById(userId);
        validateEmailVerificationState(currentUser, userId, email);

        long existed = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, email)
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                .ne(UserEntity::getUserId, userId));

        if (existed > 0) {
            log.warn("email verification skipped. email={} userId={} reason=\"email already bound\"", email, userId);
            throw new ServiceException(UserError.VERIFICATION_EMAIL_ALREADY_EXISTS);
        }

        String token = redisCacheManager.setEmailVerificationCode(email, userId);

        // 构建验证链接
        String verifyLink = userProperties.getApiDomain() + "/email/verify?token=" + token;

        // 构建验证邮件
        Context context = new Context();
        context.setVariable("username", currentUser.getUsername());
        context.setVariable("verify_link", verifyLink);
        context.setVariable("current_date", DateUtil.now());
        // Thymeleaf 渲染
        String emailContent = templateEngine.process("verifyMailTemplate", context);

        MailSendDTO mailDTO = MailSendDTO.builder().toEmail(email).subject("WisePen 邮箱验证").content(emailContent).build();

        try {
            remoteMailService.sendMail(mailDTO);
            log.info("email verification mail sent. userId={} email={}", userId, email);
        } catch (Exception e) {
            throw new ServiceException(UserError.VERIFICATION_EMAIL_SEND_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VerificationResultDTO verify(Map<String, Object> payload) {
        ImmutablePair<Long, String> verifyInfo = redisCacheManager.getEmailVerificationUser((String) payload.get("token"));
        if (verifyInfo == null) {
            throw new ServiceException(UserError.VERIFICATION_EMAIL_TOKEN_EXPIRED);
        }
        Long userId = verifyInfo.getLeft();
        String email = verifyInfo.getRight();
        rejectPartnerUniversitiesEmail(email, userId);

        EducationEmailSchool school = educationEmailSchoolResolver.findByEmail(email)
                .orElseThrow(() -> new ServiceException(UserError.VERIFICATION_EMAIL_INVALID));

        UserEntity currentUser = userMapper.selectById(userId);
        validateEmailVerificationState(currentUser, userId, email);

        // 在最终更新状态前，再次检查邮箱唯一性
        long existed = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, email)
                .eq(UserEntity::getUserStatus, UserStatus.NORMAL)
                .ne(UserEntity::getUserId, userId));

        if (existed > 0) {
            log.warn("email verification skipped. email={} userId={} reason=\"verified by other user\"", email, userId);
            throw new ServiceException(UserError.VERIFICATION_EMAIL_ALREADY_EXISTS);
        }

        UserEntity userEntity = new UserEntity();
        userEntity.setUserId(userId);
        userEntity.setEmail(email);
        userEntity.setUserStatus(UserStatus.NORMAL);
        userEntity.setVerificationMode(UserVerificationMode.EDU_EMAIL);

        if (userMapper.updateById(userEntity) != 1) {
            throw new ServiceException(UserError.VERIFICATION_EMAIL_UPDATE_FAILED);
        }

        UserProfileEntity userProfileEntity = new UserProfileEntity();
        userProfileEntity.setUserId(userId);
        userProfileEntity.setUniversity(school.getNameZh());
        if (userProfileMapper.updateById(userProfileEntity) != 1) {
            throw new ServiceException(UserError.VERIFICATION_EMAIL_UPDATE_FAILED);
        }

        redisCacheManager.updateUserStatusInSession(userId, UserStatus.NORMAL);
        log.info("email verification succeeded. userId={} emailDomain={} university={}",
                userId, school.getDomain(), school.getNameZh());
        return VerificationResultDTO.success();
    }

    @Override
    public List<String> getReadonlyFields() {
        return Arrays.asList("username", "email", "userStatus", "university");
    }

    private void validateEmailVerificationState(UserEntity userEntity, Long userId, String email) {
        if (userEntity == null
                || userEntity.getUserStatus() != UserStatus.UNIDENTIFIED // 被封禁的账号不能认证
                || userEntity.getVerificationMode() != null) {
            log.warn("email verification skipped. email={} userId={} reason=\"user state invalid\"", email, userId);
            throw new ServiceException(UserError.VERIFICATION_EMAIL_STATE_INVALID);
        }
    }

    private void rejectPartnerUniversitiesEmail(String email, Long userId) {
        String emailDomain = extractEmailDomain(email);
        boolean isPartnerUniversitiesEmail = PARTNER_UNIVERSITIES_EMAIL_DOMAINS.stream()
                .anyMatch(domain -> emailDomain.equals(domain) || emailDomain.endsWith("." + domain));
        if (isPartnerUniversitiesEmail) {
            log.warn("email verification rejected. email={} userId={} emailDomain={} reason=\"partner universities  email domain\"",
                    email, userId, emailDomain);
            throw new ServiceException(UserError.VERIFICATION_EMAIL_NOT_ALLOWED);
        }
    }

    private static String extractEmailDomain(String email) {
        if (StrUtil.isBlank(email)) {
            return "";
        }
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return "";
        }
        return email.substring(atIndex + 1).trim().toLowerCase(Locale.ROOT);
    }
}
