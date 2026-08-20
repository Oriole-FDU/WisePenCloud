package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WalletTransactionType {
    REFILL(1, "REFILL"),
    SPEND(2, "SPEND"),
    TRANSFER_IN(3, "TRANSFER_IN"),
    TRANSFER_OUT(4, "TRANSFER_OUT"),
    INCOME(5, "INCOME"),
    EXCHANGE(6, "EXCHANGE"),
    REVERSE(7, "REVERSE"),
    ONLY_RECORD_META(0, "ONLY_RECORD_META"); // 零交易，用于记录

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
}
