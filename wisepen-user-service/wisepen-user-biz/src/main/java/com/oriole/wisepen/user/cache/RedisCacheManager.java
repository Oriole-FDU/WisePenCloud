package com.oriole.wisepen.user.cache;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.UserStatus;
import com.oriole.wisepen.user.api.constant.GroupDashboardMetricConstants;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RedisCacheManager {

	private final RedisTemplate<String, Object> redisTemplate;
	private final StringRedisTemplate stringRedisTemplateDB0;
	private final StringRedisTemplate stringRedisTemplateDB1;

	public RedisCacheManager(
			RedisTemplate<String, Object> redisTemplate,
			@Qualifier("stringRedisTemplateDB0") StringRedisTemplate stringRedisTemplateDB0,
			@Qualifier("stringRedisTemplateDB1") StringRedisTemplate stringRedisTemplateDB1) {
		this.redisTemplate = redisTemplate;
		this.stringRedisTemplateDB0 = stringRedisTemplateDB0;
		this.stringRedisTemplateDB1 = stringRedisTemplateDB1;
	}

	private static final String REDIS_PWD_RESET_TOKEN_PREFIX = "wisepen:user:auth:reset:";
	private static final String REDIS_SESSION_PREFIX = "wisepen:user:auth:session:";
	private static final String REDIS_SESSION_TO_USER_PREFIX = "wisepen:user:auth:user2session:";
	private static final String REDIS_GROUP_CHAT_BLOCK_PREFIX = "wisepen:chat:block:group:";
	private static final String REDIS_GROUP_MEMBER_CHAT_BLOCK_PREFIX = "wisepen:chat:block:member:";
	private static final String REDIS_GROUP_USER_BLOCK_PREFIX = "wisepen:chat:block:user:";
	private static final String REDIS_EMAIL_VERIFY_TOKEN_PREFIX = "wisepen:user:auth:verify:";
    private static final long SESSION_TIMEOUT_DAYS = 7;

	public Map<String, Integer> listGroupDashboardMetricCounters(LocalDate statDate) {
		String indexKey = GroupDashboardMetricConstants.actorIndexKey(statDate);
		Set<String> metricKeys = stringRedisTemplateDB0.opsForSet().members(indexKey);
		if (metricKeys == null || metricKeys.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, Integer> counters = new LinkedHashMap<>();
		metricKeys.forEach(metricKey -> {
			String value = stringRedisTemplateDB0.opsForValue().get(metricKey);
			if (StrUtil.isNotBlank(value)) {
				counters.put(metricKey, Integer.parseInt(value));
			}
		});
		return counters;
	}

    public String setEmailVerificationCode(String email, Long userId) {
        // 生成6位数字 token
        String token = RandomUtil.randomNumbers(6);
        String redisKey = REDIS_EMAIL_VERIFY_TOKEN_PREFIX + token;

		String redisValue = userId + ":" + email;
		stringRedisTemplateDB0.opsForValue().set(redisKey, redisValue, 15, TimeUnit.MINUTES);

        return token;
    }

    public ImmutablePair<Long, String> getEmailVerificationUser(String token) {
        String redisKey = REDIS_EMAIL_VERIFY_TOKEN_PREFIX + token;
		String redisValue = stringRedisTemplateDB0.opsForValue().get(redisKey);
        redisTemplate.delete(redisKey); // 立即删除
		if (StrUtil.isBlank(redisValue)) {
			return null;
		}
		String[] parts = redisValue.split(":", 2);
		return ImmutablePair.of(Long.parseLong(parts[0]), parts[1]);
    }

	public String setPwdResetToken(Long userId){
		String token = IdUtil.fastSimpleUUID();
		redisTemplate.opsForValue().set(REDIS_PWD_RESET_TOKEN_PREFIX + token, userId, 15, TimeUnit.MINUTES);
		return token;
	}

	public Long getPwdResetUser(String token){
		String userId = stringRedisTemplateDB0.opsForValue().get(REDIS_PWD_RESET_TOKEN_PREFIX + token);
		redisTemplate.delete(REDIS_PWD_RESET_TOKEN_PREFIX + token); // 立即删除
		return  StrUtil.isNotBlank(userId) ? Long.parseLong(userId) : null;
	}

	public String setSession(Long userId, IdentityType identityType, UserStatus userStatus, Map<String, Integer> groupRoleMap) {
		// 构建 Session 上下文数据
		Map<String, Object> sessionData = new HashMap<>();
		sessionData.put("userId", userId);
		sessionData.put("identityType", identityType.getCode());
		sessionData.put("status", userStatus.getCode());
		sessionData.put("groupRoleMap", groupRoleMap);

		String sessionId = stringRedisTemplateDB0.opsForValue().get(REDIS_SESSION_TO_USER_PREFIX + userId);
		if (StrUtil.isBlank(sessionId)) {
			sessionId = IdUtil.fastSimpleUUID();
		}
		// sessionId不存在则新增；sessionId存在则刷新一下时间
		redisTemplate.opsForValue().set(REDIS_SESSION_PREFIX + sessionId, sessionData,
				SESSION_TIMEOUT_DAYS, TimeUnit.DAYS); // 存储Session
		stringRedisTemplateDB0.opsForValue().set(REDIS_SESSION_TO_USER_PREFIX + userId, sessionId,
				SESSION_TIMEOUT_DAYS, TimeUnit.DAYS); // 存储sessionId(建立与用户名的关联以便检索)
		return sessionId;
	}

	public void deleteSession(String sessionId, Long userId) {
		redisTemplate.delete(REDIS_SESSION_PREFIX + sessionId);
		stringRedisTemplateDB0.delete(REDIS_SESSION_TO_USER_PREFIX + userId);
	}

	/**
	 * 删除指定用户的会话（若存在），安全封装：查找 user->session 映射并删除 session 与映射
	 */
	public void deleteSessionsByUserId(Long userId) {
		String sessionId = stringRedisTemplateDB0.opsForValue().get(REDIS_SESSION_TO_USER_PREFIX + userId);
		if (sessionId == null) return;
		// 删除 session 和 user->session 映射
		redisTemplate.delete(REDIS_SESSION_PREFIX + sessionId);
		stringRedisTemplateDB0.delete(REDIS_SESSION_TO_USER_PREFIX + userId);
	}

	public void updateUserStatusInSession(Long userId, UserStatus userStatus) {
		String sessionId = stringRedisTemplateDB0.opsForValue().get(REDIS_SESSION_TO_USER_PREFIX + userId);
		if (StrUtil.isBlank(sessionId)) return;

		@SuppressWarnings("unchecked")
		Map<String, Object> sessionData = (Map<String, Object>) redisTemplate.opsForValue().get(REDIS_SESSION_PREFIX + sessionId);
		if (sessionData == null) return;

		sessionData.put("status", userStatus.getCode());
		redisTemplate.opsForValue().set(REDIS_SESSION_PREFIX + sessionId, sessionData,
				SESSION_TIMEOUT_DAYS, TimeUnit.DAYS);
	}

	public void updateGroupRoleMapInSession(Long userId, Long groupId, GroupRoleType groupRoleType) {
		String sessionId = stringRedisTemplateDB0.opsForValue().get(REDIS_SESSION_TO_USER_PREFIX + userId);
		if (StrUtil.isBlank(sessionId)) return; // 用户未登录则直接返回

		@SuppressWarnings("unchecked")
		Map<String, Object> sessionData = (Map<String, Object>) redisTemplate.opsForValue().get(REDIS_SESSION_PREFIX + sessionId);
		if (sessionData == null) return;

		@SuppressWarnings("unchecked")
		Map<String, Integer> groupRoleMap = (Map<String, Integer>) sessionData.get("groupRoleMap");
		if (groupRoleMap == null) groupRoleMap = new HashMap<>();

		if (groupRoleType.equals(GroupRoleType.NOT_MEMBER)) {
			groupRoleMap.remove(groupId.toString());
		} else {
			groupRoleMap.put(groupId.toString(), groupRoleType.getCode());
		}
		sessionData.put("groupRoleMap", groupRoleMap);

		redisTemplate.opsForValue().set(REDIS_SESSION_PREFIX + sessionId, sessionData,
				SESSION_TIMEOUT_DAYS, TimeUnit.DAYS);
	}

	// 封印/解封 群组Chat (DB1)
	public void blockGroupChat(Long groupId) {
		stringRedisTemplateDB1.opsForValue().set(REDIS_GROUP_CHAT_BLOCK_PREFIX + groupId, "1");
	}
	public void unblockGroupChat(Long groupId) {
		stringRedisTemplateDB1.delete(REDIS_GROUP_CHAT_BLOCK_PREFIX + groupId);
	}

	// 封印/解封 组成员Chat (DB1)
	public void blockGroupMemberChat(Long groupId, Long userId) {
		stringRedisTemplateDB1.opsForValue().set(REDIS_GROUP_MEMBER_CHAT_BLOCK_PREFIX + groupId + ":" + userId, "1");
	}
	public void unblockGroupMemberChat(Long groupId, Long userId) {
		stringRedisTemplateDB1.delete(REDIS_GROUP_MEMBER_CHAT_BLOCK_PREFIX + groupId + ":" + userId);
	}

	// 封印/解封 个人Chat (DB1)
	public void blockUserChat(Long groupId) {
		stringRedisTemplateDB1.opsForValue().set(REDIS_GROUP_USER_BLOCK_PREFIX + groupId, "1");
	}
	public void unblockUserChat(Long groupId) {
		stringRedisTemplateDB1.delete(REDIS_GROUP_USER_BLOCK_PREFIX + groupId);
	}
}
