package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台资源类型枚举
 * 覆盖所有受平台管理的资源类型，包括有实体文件的文档类型和无扩展名的笔记类型。
 */
@Getter
@AllArgsConstructor
public enum ResourceType {

    /** 无扩展名的笔记，由笔记服务管理 */
    NOTE(11, "NOTE", "note"),
    /** Draw.io 图，由笔记服务管理 */
    DRAWIO(12, "DRAWIO", "drawio"),

    /** PDF 文件，由文档服务管理 */
    PDF(21, "PDF", "pdf"),
    /** Office 文件，由文档服务管理 */
    DOC(22, "DOC", "doc"),
    DOCX(23, "DOCX", "docx"),
    PPT(24, "PPT", "ppt"),
    PPTX(25, "PPTX", "pptx"),
    XLS(26, "XLS", "xls"),
    XLSX(27, "XLSX", "xlsx"),

    /** 图片资源，由媒体服务管理 */
    IMAGE(41, "IMAGE", "image"),
    /** 视频资源，由媒体服务管理 */
    VIDEO(42, "VIDEO", "video"),
    /** 音频资源，由媒体服务管理 */
    AUDIO(43, "AUDIO", "audio"),

    /** 无扩展名的 Skill，由 AI资产 服务管理 */
    SKILL(31, "SKILL", "skill"),
    /** 无扩展名的 Agent，由 AI资产 服务管理 */
    AGENT(32, "AGENT", "agent"),

    /** 未知资源，兜底 */
    UNKNOWN(99, "UNKNOWN", "unknown");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;

    private final String extension;

    private static final Map<String, ResourceType> EXT_MAP = new HashMap<>();

    static {
        for (ResourceType item : values()) {
            EXT_MAP.put(item.extension, item);
        }
    }

    /**
     * 根据扩展名（大小写不敏感）查找对应枚举值。
     *
     * @param ext 扩展名或类型标识字符串，如 "docx"、"PDF"、"note"
     * @return 对应枚举值
     */
    @JsonCreator
    public static ResourceType fromExtension(String ext) {
        if (ext == null) {
            return null;
        }
        return EXT_MAP.get(ext.trim().toLowerCase());
    }
}
