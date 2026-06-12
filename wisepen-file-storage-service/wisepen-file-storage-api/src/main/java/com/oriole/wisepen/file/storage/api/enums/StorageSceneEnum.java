package com.oriole.wisepen.file.storage.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 存储场景枚举：决定文件的物理存放路径与公开级别
 */
@Getter
@AllArgsConstructor
public enum StorageSceneEnum {

    PUBLIC_IMAGE_FOR_USER(1, "PUBLIC_IMAGE_FOR_USER", "public/images/user"),    // 公开图床，如头像、文章插图
    PUBLIC_IMAGE_FOR_GROUP(2, "PUBLIC_IMAGE_FOR_GROUP", "public/images"),
    // TODO: public/images/note 是权宜之计，应在 Q2 改为 private/images/note
    PRIVATE_IMAGE_FOR_NOTE(3, "PRIVATE_IMAGE_FOR_NOTE", "public/images/note"),  // 私密图床，如笔记中的图片
    PRIVATE_DOC(4, "PRIVATE_DOC", "private/docs"),      // 文档，如 PDF、Word，永远在私有域
    PRIVATE_SKILL_ASSET(5, "PRIVATE_SKILL_ASSET", "private/skills"), // Skill 文档与脚本等私有资产
    PRIVATE_AGENT_ASSET(6, "PRIVATE_AGENT_ASSET", "private/agents"); // Agent 文档与工具等私有资产

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;
    private final String prefix;

    // 兼容此前传值
    @JsonCreator
    public static StorageSceneEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        for (StorageSceneEnum item : values()) {
            if (item.value.equalsIgnoreCase(text) || item.name().equalsIgnoreCase(text)) {
                return item;
            }
        }
        try {
            int code = Integer.parseInt(text);
            for (StorageSceneEnum item : values()) {
                if (item.code == code) {
                    return item;
                }
            }
        } catch (NumberFormatException ignored) {
            // Continue to error below.
        }
        throw new IllegalArgumentException("Unknown StorageSceneEnum: " + value);
    }
}
