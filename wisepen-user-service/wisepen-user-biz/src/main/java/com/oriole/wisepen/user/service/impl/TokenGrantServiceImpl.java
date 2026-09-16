package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.user.api.config.TokenGrantProperties;
import com.oriole.wisepen.user.api.enums.TokenGrantCode;
import com.oriole.wisepen.user.api.enums.WalletTransactionType;
import com.oriole.wisepen.user.domain.entity.TokenGrantRecordEntity;
import com.oriole.wisepen.user.mapper.TokenGrantRecordMapper;
import com.oriole.wisepen.user.service.ITokenGrantService;
import com.oriole.wisepen.user.service.IWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenGrantServiceImpl implements ITokenGrantService {

    private final TokenGrantProperties tokenGrantProperties;
    private final TokenGrantRecordMapper tokenGrantRecordMapper;
    private final IWalletService walletService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean grantMaxTimes(Long userId, TokenGrantCode grantCode, Integer maxTimes, Long operatorId, String meta) {
        if (maxTimes == null || maxTimes <= 0) {
            return false;
        }

        Long grantedTimes = tokenGrantRecordMapper.selectCount(Wrappers.<TokenGrantRecordEntity>lambdaQuery()
                .eq(TokenGrantRecordEntity::getUserId, userId)
                .eq(TokenGrantRecordEntity::getGrantCode, grantCode));
        if (grantedTimes >= maxTimes) {
            log.info("token grant skipped by total limit. userId={} grantCode={} maxTimes={}",
                    userId, grantCode.getValue(), maxTimes);
            return false;
        }

        return grant(userId, grantCode, operatorId, meta);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean grantDailyMaxTimes(Long userId, TokenGrantCode grantCode, Integer maxTimesPerDay, Long operatorId, String meta) {
        if (maxTimesPerDay == null || maxTimesPerDay <= 0) {
            return false;
        }

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Long grantedTimes = tokenGrantRecordMapper.selectCount(Wrappers.<TokenGrantRecordEntity>lambdaQuery()
                .eq(TokenGrantRecordEntity::getUserId, userId)
                .eq(TokenGrantRecordEntity::getGrantCode, grantCode)
                .ge(TokenGrantRecordEntity::getCreateTime, todayStart)
                .lt(TokenGrantRecordEntity::getCreateTime, tomorrowStart));
        if (grantedTimes >= maxTimesPerDay) {
            log.info("token grant skipped by daily limit. userId={} grantCode={} maxTimesPerDay={}",
                    userId, grantCode.getValue(), maxTimesPerDay);
            return false;
        }

        return grant(userId, grantCode, operatorId, meta);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean grantUnchecked(Long userId, TokenGrantCode grantCode, Long operatorId, String meta) {
        return grant(userId, grantCode, operatorId, meta);
    }

    private boolean grant(Long userId, TokenGrantCode grantCode, Long operatorId, String meta) {
        TokenGrantProperties.Rule rule = tokenGrantProperties.getRules().get(grantCode.getValue());
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())
                || rule.getTokenAmount() == null || rule.getTokenAmount() <= 0) {
            return false;
        }

        String walletTraceId = IdUtil.randomUUID();
        TokenGrantRecordEntity record = TokenGrantRecordEntity.builder()
                .userId(userId)
                .grantCode(grantCode)
                .tokenAmount(rule.getTokenAmount())
                .walletTraceId(walletTraceId)
                .meta(meta)
                .grantTime(LocalDateTime.now())
                .build();
        tokenGrantRecordMapper.insert(record);

        walletService.changeUserTokenBalance(
                userId,
                operatorId,
                walletTraceId,
                rule.getTokenAmount(),
                WalletTransactionType.GIFT,
                meta
        );
        return true;
    }
}
