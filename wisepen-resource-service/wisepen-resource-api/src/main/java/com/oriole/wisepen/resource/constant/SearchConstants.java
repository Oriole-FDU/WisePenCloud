package com.oriole.wisepen.resource.constant;

import java.util.List;

/**
 * 搜索域全局常量。
 * <p>
 * 索引名以 {@code wisepen_resource_index} 落在资源域命名空间下，与 MongoDB 集合
 * {@code wisepen_resource_items} 同源呼应；高亮标签使用统一的 {@code <em class="wp-highlight">}
 * 包裹，前端可对该 class 单独设置样式。
 */
public interface SearchConstants {

    /** ES 资源搜索聚合索引名称 */
    String RESOURCE_INDEX_NAME = "wisepen_resource_index";

    /** 高亮包裹前缀：前端用 {@code v-html} 渲染时通过 class 命中样式 */
    String HIGHLIGHT_PRE_TAG = "<em class=\"wp-highlight\">";

    /** 高亮包裹后缀，与 {@link #HIGHLIGHT_PRE_TAG} 成对出现 */
    String HIGHLIGHT_POST_TAG = "</em>";

    String NESTED_PATH_GROUP_ACLS = "groupAcls";

    /** 最小页码 */
    int MIN_PAGE_NUM = 1;

    /** 默认页码 */
    int DEFAULT_PAGE_NUM = 1;

    /** 每页最小数量 */
    int MIN_PAGE_SIZE = 1;

    /** 每页最大数量 */
    int MAX_PAGE_SIZE = 100;

    /** 默认每页数量 */
    int DEFAULT_PAGE_SIZE = 20;

    int COREPOOLSIZE=5;



    /**
     * IK 分词器：最细粒度切分
     * (通常用于【建索引】阶段，把一句话尽可能多地拆出词，提高召回率)
     */
    String ANALYZER_IK_MAX_WORD = "ik_max_word";

    /**
     * IK 分词器：智能粗粒度切分
     * (通常用于【搜索】阶段，根据语义拆分用户的搜索词，提高准确率)
     */
    String ANALYZER_IK_SMART = "ik_smart";

    /**
     * ES 日期格式化兼容模式。
     * 支持三种格式，用 || 隔开：
     * 1. uuuu-MM-dd'T'HH:mm:ss (标准 ISO 格式)
     * 2. uuuu-MM-dd HH:mm:ss (常规带空格格式)
     * 3. epoch_millis (毫秒时间戳)
     * 注意：ES 从 8.x (Java Time API) 推荐使用 uuuu 代替 yyyy 来表示年份。
     */
    String ES_DATE_FORMAT_PATTERN = "uuuu-MM-dd'T'HH:mm:ss||uuuu-MM-dd HH:mm:ss||epoch_millis";

    List<String> BOOSTED_SEARCH_FIELDS= List.of("resourceName^3", "content");


    /** 资源类型 */
    String FIELD_RESOURCE_TYPE = "resourceType";

    /** 白名单用户列表 */
    String FIELD_ALLOWED_USERS = "allowedUsers";

    /** 资源唯一 ID */
    String FIELD_RESOURCE_ID = "resourceId";


    /** 更新时间 */
    String FIELD_UPDATE_TIME = "updateTime";

    /** 资源名称 (标题) */
    String FIELD_RESOURCE_NAME = "resourceName";

    /** 正文内容 */
    String FIELD_CONTENT = "content";


    /** 嵌套群组 ACL 根路径字段 */
    String FIELD_GROUP_ACLS = "groupAcls"; // 替代之前的硬编码 "groupAcls"

    /** 标签列表 */
    String FIELD_TAGS = "tags";

    // --- Nested: groupAcls 下的子字段 ---

    /** 群组 ID */
    String FIELD_GROUP_ACLS_GROUP_ID = "groupAcls.groupId";

    /** 群组基础可见性 */
    String FIELD_GROUP_ACLS_BASE_DISCOVER = "groupAcls.baseDiscover";

    /** 显式被该群组否决(拉黑)的用户 */
    String FIELD_GROUP_ACLS_DENIED_USERS = "groupAcls.explicitlyDeniedUsers";


    /** 高亮片段截取长度 (单段摘要字符数) */
    int HIGHLIGHT_FRAGMENT_SIZE = 100;

    /** 最大高亮片段数 (正文中命中多次时，最多抽取几段) */
    int HIGHLIGHT_MAX_FRAGMENTS = 3;

    /** 多段高亮内容拼接时的分隔符 */
    String HIGHLIGHT_FRAGMENT_SEPARATOR = "...";

}
