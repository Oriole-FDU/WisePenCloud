package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 平台资源类型枚举
 * 覆盖所有受平台管理的资源类型，包括有实体文件的文档类型和无扩展名的笔记类型。
 */
@Getter
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

    /** 无扩展名的 Skill，由 AI资产 服务管理 */
    SKILL(31, "SKILL", "skill"),
    /** 无扩展名的 Agent，由 AI资产 服务管理 */
    AGENT(32, "AGENT", "agent"),

    /** 单文件 HTML 页面，由 Web App 服务管理 */
    SINGLE_HTML_PAGE(41, "SINGLE_HTML_PAGE", "html"),

    /** 压缩包类文件，由通用资源服务管理 */
    ARCHIVE(51, "ARCHIVE", "archive", "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz"),
    /** 明确的二进制文件，由通用资源服务管理 */
    BINARY(52, "BINARY", "binary", "bin", "dat"),

    /** 未知资源，兜底 */
    UNKNOWN(99, "UNKNOWN", "unknown");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;

    private final String extension;

    private final Set<String> extensions;

    private static final Map<String, ResourceType> TYPE_MAP = new HashMap<>();

    static {
        for (ResourceType item : values()) {
            TYPE_MAP.put(item.value.toLowerCase(Locale.ROOT), item);
            for (String extension : item.extensions) {
                TYPE_MAP.put(extension, item);
            }
        }
    }

    ResourceType(int code, String value, String extension, String... aliases) {
        this.code = code;
        this.value = value;
        this.extension = extension;
        this.extensions = new HashSet<>();
        this.extensions.add(extension);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    this.extensions.add(alias.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /**
     * 根据资源类型值或扩展名（大小写不敏感）查找对应枚举值。
     *
     * @param value 资源类型值或扩展名，如 "SINGLE_HTML_PAGE"、"html"、"docx"
     * @return 对应枚举值
     */
    @JsonCreator
    public static ResourceType fromExtension(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return TYPE_MAP.get(normalized);
    }

}
