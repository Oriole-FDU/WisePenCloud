package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch 资源搜索聚合索引实体。
 * <p>
 * 文档 ID 由 {@link #generateEsId(String, String)} 用 {@code resourceType + "_" + resourceId}
 * 拼成，保证同一资源 ID 在多类型环境下不会冲突。文本字段统一用 {@code ik_max_word} 建索引、
 * {@code ik_smart} 检索，符合 IK 分词器"宁滥勿缺"建库、"宁缺勿滥"检索的最佳实践。
 *
 * <h3>ACL 编码 — 对齐资源域的位掩码 ACL 模型</h3>
 * 与 {@code [Java] Resource 微服务整体解析.pdf} 中 {@code listResources} 的可见性过滤一致，
 * 覆盖 4 种情况 + admin/owner 短路：
 * <ul>
 *   <li>情况 A：资源所有者 → {@link #allowedUsers} 始终含 ownerId</li>
 *   <li>情况 B：资源级定向特权且 DISCOVER 位为 1 → 该 userId 写入 {@link #allowedUsers}</li>
 *   <li>情况 C：computedGroupAcls.userMasks 中 DISCOVER 位为 1 → 该 userId 写入 {@link #allowedUsers}</li>
 *   <li>情况 D：用户无专属 userMasks + 该组 baseMask 的 DISCOVER 位为 1
 *       → 编码到 {@link #groupAcls} 的 {@link GroupAcl#getBaseDiscover()}；BLACKLIST 用户写入
 *       {@link GroupAcl#getExplicitlyDeniedUsers()}，由查询时 must_not 排除</li>
 *   <li>admin/owner 短路：用户是某绑定组的 ADMIN/OWNER → 由查询时 nested groupAcls.groupId
 *       与用户管理组列表相交即可放行</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = SearchConstants.RESOURCE_INDEX_NAME, createIndex = true)
public class ESIndexEntity {

    /** ES 文档 ID，固定形如 {@code "pdf_<resourceId>"} / {@code "note_<resourceId>"} */
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String resourceId;

    /** 资源类型字面值，写入端统一使用 {@link ResourceType#getExtension()} 小写值 */
    @Field(type = FieldType.Keyword)
    private String resourceType;

    /** 资源名称，与 ResourceItemEntity.resourceName 同源 */
    @Field(type = FieldType.Text, analyzer = SearchConstants.ANALYZER_IK_MAX_WORD, searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String resourceName;

    @Field(type = FieldType.Text, analyzer = SearchConstants.ANALYZER_IK_MAX_WORD, searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String content;

    /**
     * 始终可见用户名单：owner + (specifiedUsersGrantedActionsMask 中 DISCOVER 位为 1 的用户)
     * + (各 computedGroupAcls.userMasks 中 DISCOVER 位为 1 的用户)。
     */
    @Field(type = FieldType.Keyword)
    private List<String> allowedUsers;

    /**
     * 按"非个人"绑定组逐项编码 ACL；查询时 nested 匹配以处理
     * 情况 D（基础掩码 + 黑名单）和 admin/owner 短路。
     */
    @Field(type = FieldType.Nested)
    private List<GroupAcl> groupAcls;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /** 同时支持 ISO 标准格式、空格分隔格式和毫秒时间戳，便于异构上游推数据 */
    @Field(type = FieldType.Date, format = {}, pattern = SearchConstants.ES_DATE_FORMAT_PATTERN)
    private LocalDateTime updateTime;

    /**
     * 生成防冲突的 ES 全局唯一 ID。
     *
     * @param resourceType 资源类型字面值（建议传 {@link ResourceType#getExtension()}）
     * @param resourceId   资源业务 ID
     */
    public static String generateEsId(String resourceType, String resourceId) {
        if (resourceType == null || resourceId == null) {
            throw new IllegalArgumentException("生成 ES ID 时，resourceType 和 resourceId 不能为空");
        }
        return resourceType.toLowerCase() + "_" + resourceId;
    }

    /**
     * 单个绑定组的 ACL 编码，对应 {@code ResourceItemEntity.computedGroupAcls.get(groupId)}。
     * 写入端仅生成"非个人"组（{@code calculateResourceGroupAcl} 已天然过滤个人空间）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupAcl {

        @Field(type = FieldType.Keyword)
        private String groupId;

        /** {@code computedGroupAcls.{groupId}.baseMask} 的 DISCOVER 位是否为 1 */
        @Field(type = FieldType.Boolean)
        private Boolean baseDiscover;

        /** userMasks 中 DISCOVER 位为 0 的用户（BLACKLIST 否决项） */
        @Field(type = FieldType.Keyword)
        private List<String> explicitlyDeniedUsers;
    }
}
