package com.oriole.wisepen.user.campus;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusProviderCatalogDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.CampusProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.ClassicAuthProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.domain.dto.OAuth2ProviderDescriptorDTO;
import com.oriole.wisepen.campus.extension.api.enums.CampusAuthProtocol;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.exception.UserError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 汇总校园 Provider 的登录与身份认证能力，供前端一次性渲染登录窗口和认证窗口。
 */
@Service
@RequiredArgsConstructor
public class CampusProviderCatalogService {

    private final CampusProviderProperties properties;
    private final CampusClassicAuthProviderRegistry classicAuthProviderRegistry;
    private final CampusOAuth2ProviderRegistry oauth2ProviderRegistry;

    public CampusProviderCatalogDTO listProviders() {
        List<CampusProviderDescriptorDTO> login = new ArrayList<>();
        List<CampusProviderDescriptorDTO> identity = new ArrayList<>();
        for (Map.Entry<String, CampusProviderProperties.ProviderConfig> entry : properties.getProviders().entrySet()) {
            String providerCode = entry.getKey();
            CampusProviderProperties.ProviderConfig config = entry.getValue();
            if (config == null || StrUtil.isBlank(config.getServiceName())) {
                continue;
            }
            if (config.getLogin() != null) {
                for (CampusAuthProtocol protocol : config.getLogin()) {
                    login.add(describe(providerCode, protocol, protocol == CampusAuthProtocol.OAUTH2 ? UserError.AUTH_CAMPUS_OAUTH_PROVIDER_NOT_FOUND : UserError.AUTH_CAMPUS_CLASSIC_PROVIDER_NOT_FOUND));
                }
            }
            if (config.getIdentity() != null) {
                for (CampusAuthProtocol protocol : config.getIdentity()) {
                    identity.add(describe(providerCode, protocol, protocol == CampusAuthProtocol.OAUTH2 ? UserError.AUTH_CAMPUS_OAUTH_PROVIDER_NOT_FOUND : UserError.AUTH_CAMPUS_CLASSIC_PROVIDER_NOT_FOUND));
                }
            }
        }
        return CampusProviderCatalogDTO.builder().login(login).identity(identity).build();
    }

    /** 按协议向对应扩展服务拉取 descriptor，并归一化为统一入口描述。 */
    private CampusProviderDescriptorDTO describe(String providerCode, CampusAuthProtocol protocol, UserError error) {
        if (protocol == CampusAuthProtocol.OAUTH2) {
            OAuth2ProviderDescriptorDTO descriptor = requireRemoteData(
                    oauth2ProviderRegistry.require(providerCode).getProviderDescriptor(), error);
            return CampusProviderDescriptorDTO.builder()
                    .providerCode(providerCode)
                    .displayName(descriptor.getDisplayName())
                    .universityName(descriptor.getUniversityName())
                    .protocol(CampusAuthProtocol.OAUTH2)
                    .build();
        }
        ClassicAuthProviderDescriptorDTO descriptor = requireRemoteData(
                classicAuthProviderRegistry.require(providerCode).getProviderDescriptor(), error);
        return CampusProviderDescriptorDTO.builder()
                .providerCode(providerCode)
                .displayName(descriptor.getDisplayName())
                .universityName(descriptor.getUniversityName())
                .protocol(CampusAuthProtocol.CLASSIC)
                .initiateInputs(descriptor.getInitiateInputs())
                .supportedChallenges(descriptor.getSupportedChallenges())
                .build();
    }

    private <T> T requireRemoteData(R<T> response, UserError error) {
        if (response == null || !Integer.valueOf(200).equals(response.getCode())) {
            if (response != null && StrUtil.isNotBlank(response.getMsg())) throw new ServiceException(error, response.getMsg());
            throw new ServiceException(error);
        }
        if (response.getData() == null) throw new ServiceException(error);
        return response.getData();
    }
}
