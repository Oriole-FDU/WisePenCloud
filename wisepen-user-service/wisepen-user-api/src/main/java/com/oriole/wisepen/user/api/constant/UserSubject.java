package com.oriole.wisepen.user.api.constant;

import com.oriole.wisepen.common.core.domain.IBusinessSubject;

import java.util.Locale;

public enum UserSubject implements IBusinessSubject {
    AUTH,
    AUTH_VERIFICATION,
    USER,
    GROUP,
    GROUP_MEMBER,
    GROUP_ANNOUNCEMENT,
    MESSAGE,
    WALLET,
    WALLET_TOKEN,
    WALLET_COIN,
    VOUCHER;

    @Override
    public String key() {
        return name().toLowerCase(
                Locale.ROOT);
    }
}
