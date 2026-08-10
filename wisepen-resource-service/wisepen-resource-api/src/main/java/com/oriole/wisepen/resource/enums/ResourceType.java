package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
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

    /** 压缩包类文件，由通用资源服务管理 */
    ARCHIVE(41, "ARCHIVE", "archive", "zip", "rar", "7z", "tar", "gz", "tgz", "tar.gz", "bz2", "tar.bz2", "xz", "tar.xz"),
    /** 明确的二进制文件，由通用资源服务管理 */
    BINARY(42, "BINARY", "binary", "bin", "dat"),

    /** 未知资源，兜底 */
    UNKNOWN(99, "UNKNOWN", "unknown");

    private final int code;

    @EnumValue
    @JsonValue
    private final String value;

    private final String extension;

    private final Set<String> extensions;

    private static final Map<String, ResourceType> EXT_MAP = new HashMap<>();
    private static final Set<String> COMPOUND_EXTENSIONS = Set.of("tar.gz", "tar.bz2", "tar.xz");

    static {
        for (ResourceType item : values()) {
            for (String extension : item.extensions) {
                EXT_MAP.put(extension, item);
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
                    this.extensions.add(alias.trim().toLowerCase());
                }
            }
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
        String normalized = ext.trim().toLowerCase();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return EXT_MAP.get(normalized);
    }

    public static Set<String> compoundExtensions() {
        return COMPOUND_EXTENSIONS;
    }
}
