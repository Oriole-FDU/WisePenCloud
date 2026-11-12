package com.oriole.wisepen.system.service.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务配置静态目录
 */
@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class BusinessConfigCatalog {

    /** 每个微服务在独立目录下维护一份同名目录文件 */
    private static final String CATALOG_PATTERN = "classpath*:config-catalog/*/config-catalog.json";

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private Map<String, BusinessConfigDefinition> definitions = Map.of();

    /**
     * 加载并校验全部业务配置目录
     */
    @PostConstruct
    public void loadCatalogs() throws Exception {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length != 1) {
            // 没有 profile 或同时激活多个 profile 时，System 无法唯一决定应该管理哪个环境的 Data ID
            throw new IllegalStateException("业务配置目录要求且仅允许一个 active profile");
        }
        // 从当前应用及其依赖的 classpath 中扫描约定位置下的全部微服务目录
        Resource[] catalogResources = new PathMatchingResourcePatternResolver().getResources(CATALOG_PATTERN);

        ObjectMapper catalogObjectMapper = objectMapper.copy();
        catalogObjectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION); // 禁止重复 JSON key
        // 按 JSON Schema Draft 2020-12 编译和校验
        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        Map<String, BusinessConfigDefinition> loadedDefinitions = new LinkedHashMap<>();
        Set<String> loadedServices = new HashSet<>();

        // 每个资源对应一个微服务的 config-catalog.json
        for (Resource catalogResource : catalogResources) {
            JsonNode catalogNode;
            try (InputStream inputStream = catalogResource.getInputStream()) {
                // 解析当前目录文件
                catalogNode = catalogObjectMapper.readTree(inputStream);
            }

            if (catalogNode == null || !catalogNode.isObject()) {
                throw new IllegalStateException("业务配置目录格式或版本无效: " + catalogResource.getDescription());
            }

            String service = catalogNode.path("service").asText();
            String dataIdTemplate = catalogNode.path("dataId").asText();
            String group = catalogNode.path("group").asText();
            JsonNode configsNode = catalogNode.path("configs");

            // 检查必要项目是否缺失
            if (!StringUtils.hasText(service) || !StringUtils.hasText(dataIdTemplate) || !StringUtils.hasText(group) || !configsNode.isObject()) {
                throw new IllegalStateException("业务配置目录缺少 service、dataId、group 或 configs: " + catalogResource.getDescription());
            }

            if (!loadedServices.add(service)) {
                // 同一微服务出现第二份目录会产生两个目录来源
                throw new IllegalStateException("每个微服务只允许一个业务配置目录: " + service);
            }

            String dataId = dataIdTemplate.replace("{profile}", activeProfiles[0]);
            if (dataId.contains("{") || dataId.contains("}")) { // 检查是否有其他异常占位符
                throw new IllegalStateException("业务配置目录包含不支持的 dataId 占位符: " + catalogResource.getDescription());
            }

            Iterator<Map.Entry<String, JsonNode>> configFields = configsNode.fields();
            while (configFields.hasNext()) {
                // 取得当前 code 和对应配置定义节点
                Map.Entry<String, JsonNode> configField = configFields.next();
                String code = configField.getKey();
                JsonNode configNode = configField.getValue();

                String name = configNode.path("name").asText();
                String description = configNode.path("description").asText("");
                JsonNode pathNode = configNode.path("path"); // path 使用字符串数组逐层定位 YAML Object
                JsonNode restartRequiredNode = configNode.get("restartRequired"); // 明确声明发布后是否需要重启服务实例
                JsonNode schemaNode = configNode.path("schema"); // schema 同时提供给前端渲染，并由后端在发布前执行校验

                // 检查必要项目是否缺失
                // code、name、非空数组 path、restartRequired 和对象 schema 是每项配置的最小必要定义
                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)
                        || !pathNode.isArray() || pathNode.isEmpty()
                        || restartRequiredNode == null || !restartRequiredNode.isBoolean()
                        || !schemaNode.isObject()) {
                    throw new IllegalStateException("业务配置定义缺少 name、path、restartRequired 或 schema: " + code);
                }

                // 检查路径合法性
                List<String> path = new ArrayList<>();
                for (JsonNode pathSegmentNode : pathNode) {
                    String pathSegment = pathSegmentNode.asText();
                    if (!pathSegmentNode.isTextual() || !StringUtils.hasText(pathSegment)) {
                        // 非字符串或空 key 都会使 YAML 节点定位语义不明确
                        throw new IllegalStateException("业务配置路径只能包含非空字符串: " + code);
                    }
                    path.add(pathSegment);
                }

                // Schema 在启动时编译一次，运行期应用配置时直接复用编译结果
                JsonSchema compiledSchema;
                try {
                    compiledSchema = schemaFactory.getSchema(schemaNode);
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("业务配置 Schema 无效: " + code, exception);
                }

                // 将目录公共定位信息和当前节点定义合并成一份完整运行期定义
                BusinessConfigDefinition definition = new BusinessConfigDefinition(
                        code, name, description, service, dataId, group, List.copyOf(path),
                        restartRequiredNode.booleanValue(), schemaNode.deepCopy(), compiledSchema
                );
                // putIfAbsent 在保存定义的同时检查跨微服务全局 code 唯一性
                if (loadedDefinitions.putIfAbsent(code, definition) != null) {
                    throw new IllegalStateException("业务配置 code 重复: " + code);
                }
            }
        }

        // 路径冲突检查
        List<BusinessConfigDefinition> definitionList = new ArrayList<>(loadedDefinitions.values());
        // 左侧定义遍历到最后一个定义即可完成所有无序组合比较
        for (int leftIndex = 0; leftIndex < definitionList.size(); leftIndex++) {
            BusinessConfigDefinition left = definitionList.get(leftIndex);
            // 右侧从左侧后一项开始，避免和自身比较以及重复比较同一对定义
            for (int rightIndex = leftIndex + 1; rightIndex < definitionList.size(); rightIndex++) {
                BusinessConfigDefinition right = definitionList.get(rightIndex);
                // 只有同一 group 和 dataId 的两个节点才可能在同一份 YAML 文档中发生覆盖
                if (!left.getDataId().equals(right.getDataId()) || !left.getGroup().equals(right.getGroup())) {
                    continue;
                }

                // 比较到较短路径结束即可判断两条路径是否完全相同或互为父子
                int sharedLength = Math.min(left.getPath().size(), right.getPath().size());
                boolean pathOverlaps = true;
                // 从根 key 开始比较两条路径的共同长度
                for (int pathIndex = 0; pathIndex < sharedLength; pathIndex++) {
                    // 当前层 key 不同，说明两条路径已经分叉，不会互相覆盖
                    if (!left.getPath().get(pathIndex).equals(right.getPath().get(pathIndex))) {
                        pathOverlaps = false;
                        break;
                    }
                }
                // 共同长度内完全一致，意味着路径重复，或较短路径是较长路径的父节点
                if (pathOverlaps) {
                    throw new IllegalStateException("同一 Nacos Data ID 中的业务配置路径不能重复或互为父子: "
                            + left.getCode() + ", " + right.getCode());
                }
            }
        }

        // 所有目录完成校验后一次性发布不可变快照
        definitions = Collections.unmodifiableMap(loadedDefinitions);
        log.info("business config catalogs loaded. catalogCount={} configCount={} profile={}",
                catalogResources.length, definitions.size(), activeProfiles[0]);
    }
}
