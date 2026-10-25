package com.oriole.wisepen.common.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum GroupType {
	NORMAL_GROUP(1, "NORMAL_GROUP"),
	ADVANCED_GROUP(2, "ADVANCED_GROUP"),
	MARKET_GROUP(3, "MARKET_GROUP");


	@EnumValue
	private final int code;

	@JsonValue
	private final String value;

	@JsonCreator
	public static GroupType fromValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return Arrays.stream(values())
				.filter(type -> type.value.equals(value) || String.valueOf(type.code).equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown GroupType: " + value));
	}

	public static GroupType getByCode(Integer code) {
		if (code == null) {return null;}
		return Arrays.stream(values())
				.filter(t -> t.getCode() == code)
				.findFirst()
				.orElse(null);
	}
}
