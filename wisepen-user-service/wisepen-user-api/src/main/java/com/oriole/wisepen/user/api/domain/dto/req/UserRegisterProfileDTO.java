package com.oriole.wisepen.user.api.domain.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oriole.wisepen.user.api.domain.base.UserProfileBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties("inviteCode")
public class UserRegisterProfileDTO extends UserProfileBase {
}
