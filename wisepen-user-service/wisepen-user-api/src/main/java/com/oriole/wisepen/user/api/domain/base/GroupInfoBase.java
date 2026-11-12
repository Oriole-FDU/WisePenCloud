package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.AiBillingPolicy;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class GroupInfoBase extends GroupDisplayBase{
    private Long ownerId;
    private String inviteCode;
    private Integer memberCount;

    private Integer tokenUsed;
    private Integer tokenBalance;

    private AiBillingPolicy aiBillingPolicy;
}
