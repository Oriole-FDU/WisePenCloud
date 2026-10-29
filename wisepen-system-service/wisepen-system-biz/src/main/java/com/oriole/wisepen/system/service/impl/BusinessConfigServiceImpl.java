package com.oriole.wisepen.system.service.impl;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.networknt.schema.ValidationMessage;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.system.api.domain.dto.req.BusinessConfigApplyRequest;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigApplyResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigServiceResponse;
import com.oriole.wisepen.system.api.domain.dto.res.BusinessConfigSummaryResponse;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.service.BusinessConfigService;
import com.oriole.wisepen.system.service.config.BusinessConfigCatalog;
import com.oriole.wisepen.system.service.config.BusinessConfigDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 业务配置查询与应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessConfigServiceImpl implements BusinessConfigService {

    private final BusinessConfigCatalog businessConfigCatalog;
    private final NacosConfigManager nacosConfigManager;
    private final ObjectMapper objectMapper;

    /**
     * 用于完整 Nacos YAML 的解析和序列化
     *
     * 关闭文档起始标记
     * 开启重复 key 检测，避免读取时由后值静默覆盖前值
     */
    private final ObjectMapper yamlObjectMapper = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    ).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /**
     * 查询静态目录摘要
     */
    @Override
    public List<BusinessConfigSummaryResponse> listConfigs() {
        List<BusinessConfigSummaryResponse> responses = new ArrayList<>();
        businessConfigCatalog.getDefinitions().values().stream()
                // 先按所属微服务排序，再按配置 code 排序，保证前端每次取得相同顺序
                .sorted(Comparator.comparing(BusinessConfigDefinition::getService)
                        .thenComparing(BusinessConfigDefinition::getCode))
                .forEach(definition -> {
                    BusinessConfigSummaryResponse response = new BusinessConfigSummaryResponse();
                    response.setCode(definition.getCode()); // code 是查询详情和应用配置时使用的稳定外部标识
                    response.setName(definition.getName()); // name 用于管理端展示配置名称
                    response.setDescription(definition.getDescription()); // description 用于说明该配置节点的业务作用
                    response.setService(definition.getService()); // service 用于前端按微服务归类
                    response.setRestartRequired(definition.isRestartRequired()); // restartRequired 提示发布后是否需要重启实例
                    responses.add(response); // 将组装完成的摘要加入返回列表
                });
        return responses;
    }

    /**
     * 查询指定微服务的全部配置项、Schema、当前 JSON 值和完整文档 revision
     */
    @Override
    public BusinessConfigServiceResponse listServiceConfigs(String service) {
        // 同一个微服务只对应一份目录和一个 Nacos Data ID，取出该服务的全部节点定义
        List<BusinessConfigDefinition> definitions = businessConfigCatalog.getDefinitions().values().stream()
                .filter(definition -> definition.getService().equals(service))
                .sorted(Comparator.comparing(BusinessConfigDefinition::getCode))
                .toList();
        if (definitions.isEmpty()) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_NOT_FOUND);
        }

        BusinessConfigDefinition serviceDefinition = definitions.getFirst();
        String yamlContent;
        try {
            yamlContent = nacosConfigManager.getConfigService().getConfig(
                    serviceDefinition.getDataId(), serviceDefinition.getGroup(),
                    nacosConfigManager.getNacosConfigProperties().getTimeout()
            );
        } catch (NacosException exception) {
            log.error("business configs query failed. service={} dataId={} group={}",
                    service, serviceDefinition.getDataId(), serviceDefinition.getGroup(), exception);
            throw new ServiceException(SysError.BUSINESS_CONFIG_NACOS_FAILED);
        }

        // Data ID 必须预先存在，业务配置管理器只负责读取或创建其中的节点
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_NOT_FOUND, "Nacos Data ID 不存在或内容为空");
        }

        // 完整 YAML 解析解析成 JSON 兼容树，提取该服务的全部受管节点
        JsonNode documentNode;
        try {
            documentNode = yamlObjectMapper.readTree(yamlContent);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "Nacos YAML 无法解析");
        }
        if (documentNode == null || !documentNode.isObject()) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "Nacos YAML 根节点必须是对象");
        }

        List<BusinessConfigResponse> configResponses = new ArrayList<>();
        for (BusinessConfigDefinition definition : definitions) {
            JsonNode configNode = documentNode;
            boolean exists = true;
            for (String pathSegment : definition.getPath()) {
                if (!configNode.isObject()) {
                    // 已有父路径不是对象表示现有 YAML 结构冲突
                    throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "业务配置父路径不是对象: configCode=" + definition.getCode());
                }
                if (!configNode.has(pathSegment)) {
                    exists = false;
                    break;
                }
                configNode = configNode.get(pathSegment);
            }

            BusinessConfigResponse configResponse = new BusinessConfigResponse();
            configResponse.setCode(definition.getCode());
            configResponse.setName(definition.getName());
            configResponse.setDescription(definition.getDescription());
            configResponse.setRestartRequired(definition.isRestartRequired());
            // 将目录中的 Schema JsonNode 转成普通 Map
            configResponse.setSchema(objectMapper.convertValue(
                    definition.getSchema(), new TypeReference<>() {
                    }
            ));
            configResponse.setExists(exists);
            // 将目标 JsonNode 转成普通 JSON 兼容值
            configResponse.setValue(exists ? objectMapper.convertValue(configNode, Object.class) : null);
            configResponses.add(configResponse);
        }

        BusinessConfigServiceResponse response = new BusinessConfigServiceResponse();
        response.setService(service);
        response.setConfigs(configResponses);
        // revision 对 Nacos 原始 UTF-8 字符串计算
        response.setRevision(DigestUtils.md5DigestAsHex(yamlContent.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    /**
     * 校验并应用一个业务配置节点
     */
    @Override
    public BusinessConfigApplyResponse applyConfig(BusinessConfigApplyRequest request) {
        BusinessConfigDefinition definition = businessConfigCatalog.getDefinitions().get(request.getCode());
        if (definition == null) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_NOT_FOUND, "configCode=" + request.getCode());
        }

        // 将请求中的普通 Java 值转换为 JsonNode，供 JSON Schema validator 和 YAML 树替换使用
        JsonNode newConfigNode = objectMapper.valueToTree(request.getValue());
        // 使用启动时已经编译的 Schema 验证整个目标节点
        Set<ValidationMessage> validationMessages = definition.getCompiledSchema().validate(newConfigNode);

        if (!validationMessages.isEmpty()) {
            String validationMessage = validationMessages.stream()
                    .map(ValidationMessage::getMessage)
                    .sorted()
                    .reduce((left, right) -> left + "; " + right)
                    .orElse(SysError.BUSINESS_CONFIG_CONTENT_INVALID.getMsg());
            // 返回校验错误，不执行 Nacos 操作
            throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, validationMessage);
        }

        // 重新读取完整文档
        String yamlContent;
        try {
            // 使用目录确定的 dataId、group 读取完整文档
            yamlContent = nacosConfigManager.getConfigService().getConfig(
                    definition.getDataId(), definition.getGroup(),
                    nacosConfigManager.getNacosConfigProperties().getTimeout()
            );
        } catch (NacosException exception) {
            log.error("business config query failed. configCode={} dataId={} group={}",
                    request.getCode(), definition.getDataId(), definition.getGroup(), exception);
            throw new ServiceException(SysError.BUSINESS_CONFIG_NACOS_FAILED);
        }

        if (yamlContent == null || yamlContent.isBlank()) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_NOT_FOUND, "Nacos Data ID 不存在或内容为空");
        }

        // 基于本次重新读取的 Nacos 原文计算当前完整文档 MD5。
        String currentRevision = DigestUtils.md5DigestAsHex(yamlContent.getBytes(StandardCharsets.UTF_8));
        if (!currentRevision.equalsIgnoreCase(request.getRevision())) {
            // 管理端 revision 不等于当前文档，说明查询后同一 Data ID 的任意节点已经变化
            // 要求管理端重新查询最新节点和 revision 后再提交
            throw new ServiceException(SysError.BUSINESS_CONFIG_REVISION_CONFLICT);
        }

        JsonNode documentNode;
        try {
            documentNode = yamlObjectMapper.readTree(yamlContent);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "Nacos YAML 无法解析");
        }

        if (documentNode == null || !documentNode.isObject()) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "Nacos YAML 根节点必须是对象");
        }

        ObjectNode parentNode = (ObjectNode) documentNode;
        // 最后一个 path 元素是要设置的 key，这里只遍历并按需创建它之前的父路径
        for (int pathIndex = 0; pathIndex < definition.getPath().size() - 1; pathIndex++) {
            String pathSegment = definition.getPath().get(pathIndex);
            JsonNode nextNode = parentNode.get(pathSegment);
            if (nextNode == null) {
                // 缺失父节点时创建空对象，使目录中登记但 Nacos 尚未配置的节点可以首次应用
                ObjectNode newParentNode = yamlObjectMapper.createObjectNode();
                parentNode.set(pathSegment, newParentNode);
                parentNode = newParentNode;
                continue;
            }
            if (!nextNode.isObject()) {
                // 已有父路径不是对象表示现有 YAML 结构冲突
                throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "业务配置父路径不是对象: configCode=" + request.getCode());
            }
            parentNode = (ObjectNode) nextNode;
        }

        // 目标节点不存在时创建，存在时整体替换
        String targetPathSegment = definition.getPath().get(definition.getPath().size() - 1);
        parentNode.set(targetPathSegment, newConfigNode);

        String newYamlContent;
        try {
            newYamlContent = yamlObjectMapper.writeValueAsString(documentNode);
            yamlObjectMapper.readTree(newYamlContent);
        } catch (JsonProcessingException exception) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_CONTENT_INVALID, "更新后的 YAML 无法生成");
        }

        // Nacos CAS 的布尔返回值用于区分发布成功和并发版本冲突
        boolean published;
        try {
            // 执行服务端 CAS 发布
            published = nacosConfigManager.getConfigService().publishConfigCas(
                    definition.getDataId(), definition.getGroup(), newYamlContent, "yaml", currentRevision
            );
        } catch (NacosException exception) {
            log.error("business config publish failed. configCode={} dataId={} group={}",
                    request.getCode(), definition.getDataId(), definition.getGroup(), exception);
            throw new ServiceException(SysError.BUSINESS_CONFIG_NACOS_FAILED);
        }

        // CAS 返回 false 表示读取后服务端内容又发生变化
        if (!published) {
            throw new ServiceException(SysError.BUSINESS_CONFIG_REVISION_CONFLICT);
        }

        log.info("business config published. configCode={} dataId={} group={}",
                request.getCode(), definition.getDataId(), definition.getGroup());

        BusinessConfigApplyResponse response = new BusinessConfigApplyResponse();
        response.setPublished(true);
        response.setRevision(DigestUtils.md5DigestAsHex(newYamlContent.getBytes(StandardCharsets.UTF_8)));
        return response;
    }
}
