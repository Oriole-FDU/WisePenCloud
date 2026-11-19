package com.oriole.wisepen.user.campus.login;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.promeg.pinyinhelper.Pinyin;
import com.oriole.wisepen.campus.extension.api.domain.dto.VerifiedIdentityClaimsDTO;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.campus.IdentityClaimsApplier;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.domain.entity.UserWalletEntity;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.mapper.UserProfileMapper;
import com.oriole.wisepen.user.mapper.UserWalletsMapper;
import com.oriole.wisepen.user.service.IGroupMemberService;
import com.oriole.wisepen.user.service.IUserInviteService;
import com.oriole.wisepen.user.strategy.login.UserLoginCompleteStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校园登录完成策略：按标准 claims 匹配或自动注册账号并建立后端会话。
 * claims 已由调用方完成协议与学工号校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampusLoginCompleteStrategy
        implements UserLoginCompleteStrategy<UserLoginCompleteStrategy.CampusProviderCompleteCommand> {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    private final UserWalletsMapper userWalletsMapper;
    private final RedisCacheManager redisCacheManager;
    private final IUserInviteService userInviteService;

    private final IGroupMemberService groupMemberService;
    private final IdentityClaimsApplier identityClaimsApplier;

    @Override
    public String completeLogin(UserLoginCompleteStrategy.CampusProviderCompleteCommand command) {
        String providerCode = command.providerCode();
        VerifiedIdentityClaimsDTO claims = command.claims();

        UserEntity user = getUserCoreInfoByCampusNo(claims.getCampusNo());
        if (user == null) {
            user = campusRegister(providerCode, claims);
        }
        if (user.getUserStatus() == UserStatus.BANNED) {
            throw new ServiceException(UserError.AUTH_USER_LOCKED);
        }

        Map<String, Integer> groupRoleMap = groupMemberService.getGroupRoleMapByUserId(user.getUserId());
        String sessionId = redisCacheManager.setSession(user.getUserId(), user.getIdentityType(), user.getUserStatus(), groupRoleMap);
        log.info("campus login succeeded. providerCode={} campusNo={} userId={} groupCount={}",
                providerCode, claims.getCampusNo(), user.getUserId(), groupRoleMap.size());
        return sessionId;
    }

    public UserEntity getUserCoreInfoByCampusNo(String campusNo) {
        return userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getCampusNo, campusNo)
                .last("LIMIT 1"));
    }

    public UserEntity campusRegister(String providerCode, VerifiedIdentityClaimsDTO claims) {
        String campusNo = claims.getCampusNo();
        identityClaimsApplier.assertCampusNoAvailable(campusNo, null, UserError.CAMPUS_NO_ALREADY_EXISTS);

        UserEntity user = identityClaimsApplier.buildVerifiedUserEntity(null, providerCode, claims);

        // 校园登录只拿到学工号、缺少真实姓名时，不把账号标记为已完成身份认证，仅注册为未认证账号
        if (StrUtil.isBlank(claims.getRealName())) {
            user.setUserStatus(UserStatus.UNIDENTIFIED);
            user.setVerificationMode(null);
            user.setVerificationProviderCode(null);
            log.info("campus user registered without verified profile. providerCode={} campusNo={}", providerCode, campusNo);
        }

        // 根据真实姓名生成用户名
        user.setUsername(resolveUniqueUsername(claims.getRealName(), providerCode, campusNo));
        user.setNickname(StrUtil.isNotBlank(claims.getRealName()) ? claims.getRealName() : providerCode + "_" + campusNo);
        // SSO 用户无登录密码，写入随机占位密文，使密码登录天然失败
        user.setPassword(BCrypt.hashpw(RandomUtil.randomString(32)));
        userMapper.insert(user);

        UserProfileEntity userProfile = UserProfileEntity.builder()
                .userId(user.getUserId())
                .inviteCode(userInviteService.generateInviteCode())
                .university(claims.getUniversityName())
                .college(claims.getCollege())
                .build();
        userProfileMapper.insert(userProfile);

        UserWalletEntity userWallets = UserWalletEntity.builder().userId(user.getUserId())
                .tokenBalance(0).tokenUsed(0).coinBalance(0).build();
        userWalletsMapper.insert(userWallets);

        IdentityType identityType = identityClaimsApplier.resolveIdentityType(claims);
        log.info("campus user registered. providerCode={} campusNo={} userId={} identityType={}",
                providerCode, campusNo, user.getUserId(), identityType);
        return user;
    }

    /** 以真实姓名拼音作为用户名；冲突时追加递增数字保证全局唯一 */
    private String resolveUniqueUsername(String realName, String providerCode, String campusNo) {
        String base = Pinyin.toPinyin(StrUtil.blankToDefault(realName, ""), "").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(base)) base = providerCode + campusNo;
        // 用户名上限 20 位，预留 2 位给递增后缀
        if (base.length() > 18) { base = base.substring(0, 18); }
        // 一次查出所有以 base 开头的用户名，避免逐候选查库
        Set<String> occupied = userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .select(UserEntity::getUsername)
                        .likeRight(UserEntity::getUsername, base))
                .stream().map(UserEntity::getUsername).collect(Collectors.toSet());
        for (int seq = 1; seq <= 99; seq++) {
            String candidate = seq == 1 ? base : base + "_" + seq;
            if (!occupied.contains(candidate)) {
                return candidate;
            }
        }
        throw new ServiceException(UserError.USERNAME_ALREADY_EXISTS);
    }
}
