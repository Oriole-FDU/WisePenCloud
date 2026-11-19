package com.oriole.wisepen.user.campus;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.exception.UserError;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务侧的校园 Provider 注册表
 *
 */
@Data
@Component
@ConfigurationProperties(prefix = "wisepen.campus")
public class CampusProviderProperties {

    /** Provider 编码到配置的映射，Key 需要与前端传入的 providerCode 保持一致 */
    private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

    /** 校验 Provider 是否声明了指定登录认证协议 */
    public void requireLoginProtocol(String providerCode, CampusAuthProtocol protocol, UserError error) {
        ProviderConfig config = providers.get(providerCode);
        if (config == null || config.getLogin() == null || !config.getLogin().contains(protocol)) {
            throw new ServiceException(error);
        }
    }

    /** 校验 Provider 是否声明了指定身份认证协议 */
    public void requireIdentityProtocol(String providerCode, CampusAuthProtocol protocol, UserError error) {
        ProviderConfig config = providers.get(providerCode);
        if (config == null || config.getIdentity() == null || !config.getIdentity().contains(protocol)) {
            throw new ServiceException(error);
        }
    }

    @Data
    public static class ProviderConfig {
        /** 注册中心中的扩展服务名 */
        private String serviceName;
        /** 登录协议集合；为空表示该校不支持校园登录 */
        private List<CampusAuthProtocol> login = new ArrayList<>();
        /** 身份认证协议集合；为空表示该校不支持校园身份认证 */
        private List<CampusAuthProtocol> identity = new ArrayList<>();
    }
}
