package com.oriole.wisepen.user.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GroupErrorCode implements IErrorCode {            // 对应 NotLoginException
	NO_PERMISSION(403, "没有权限访问该资源"),
	ERROR(445,"ERROR"),
	// 业务模块 (2000-2999)
	GROUP_IS_EXISTED(2001,"该组已存在"),
	GROUP_NOT_EXIST(2002,"该组不存在"),
	PAGE_NOT_EXIST(2003,"该页不存在"),
	MEMBER_NOT_IN_GROUP(2004,"该成员不在该组中"),
	MEMBER_IS_OWNER(2005,"组长不能退出小组"),
	PERMISSION_IS_LOWER(2006,"权限不够"),
	MEMBER_NOT_EXSIT(2007,"该组没有成员"),
	MEMBER_IS_EXISTED(2008,"该成员已存在于该组"),
	DO_NOT_UPDATE_YOURSELF(2009,"禁止操作自己"),
	NORMAL_GROUP(2010,"普通组没有配额");

	// ..暂无

	private final Integer code;
	private final String msg;
}