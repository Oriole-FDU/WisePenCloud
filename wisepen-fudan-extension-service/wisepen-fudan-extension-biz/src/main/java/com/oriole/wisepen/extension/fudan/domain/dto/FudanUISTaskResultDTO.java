package com.oriole.wisepen.extension.fudan.domain.dto;

import com.oriole.wisepen.campus.extension.api.enums.CampusIdentityTaskState;
import lombok.Data;

import java.util.Map;

/**
 * 复旦 UIS 扩展服务内部任务快照
 * 该 DTO 只用于 Redis 和浏览器任务之间的内部传递
 */
@Data
public class FudanUISTaskResultDTO {

    /** WisePen 用户 ID，用于兼容旧入口和成功结果归属校验 */
    private Long userId;

    /** 该次 UIS 认证尝试的独立任务标识 */
    private String taskId;

    /** 复旦 UIS 任务状态码 */
    private CampusIdentityTaskState state;

    /** 面向扩展服务日志和内部诊断的状态描述 */
    private String message;

     /** 复旦登录页面生成的二维码 Base64 内容，仅在等待扫码状态下存在 */
    private String qrBase64;

    /** UIS 登录成功后解析出的原始资料，键名保持复旦页面字段原文 */
    private Map<String, String> profile;

    public static FudanUISTaskResultDTO of(Long userId, String taskId, CampusIdentityTaskState state) {
        FudanUISTaskResultDTO dto = new FudanUISTaskResultDTO();
        dto.setUserId(userId);
        dto.setTaskId(taskId);
        dto.setState(state);
        return dto;
    }

    public static FudanUISTaskResultDTO of(Long userId, String taskId, CampusIdentityTaskState state, String message) {
        FudanUISTaskResultDTO resultDTO = of(userId, taskId, state);
        resultDTO.setMessage(message);
        return resultDTO;
    }
}
