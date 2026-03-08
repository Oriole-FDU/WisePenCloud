package com.oriole.wisepen.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GroupIdentity {

	OWNER(1, "OWNER"),
	ADMIN(2, "ADMIN"),
	MEMBER(3, "MEMBER");

	@EnumValue
	@JsonValue
	private final Integer code;

	private final String desc;
}
