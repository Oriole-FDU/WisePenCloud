package com.oriole.wisepen.resource.enums;


import lombok.Getter;

/** Upsert 时按需要更新的逻辑字段分组 */
@Getter
public enum UpsertField {
    /** 资源名称 */
    RESOURCE_NAME,
    /** 正文长文本 */
    CONTENT,
    /** allowedUsers / groupAcls 权限编码 */
    ACL,
    /** tags 列表 */
    TAGS
}
