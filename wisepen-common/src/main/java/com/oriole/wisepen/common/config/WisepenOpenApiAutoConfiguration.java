package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({OpenAPI.class, GlobalOperationCustomizer.class})
@EnableConfigurationProperties(WisepenOpenApiProperties.class)
public class WisepenOpenApiAutoConfiguration {

//    private static final String SCHEME_FROM_SOURCE = "FromSource";
    private static final String SCHEME_USER_ID = "UserID";
    private static final String SCHEME_IDENTITY_TYPE = "IdentityType";
    private static final String SCHEME_USER_STATUS = "UserStatus";
    private static final String SCHEME_GROUP_ROLE_MAP = "GroupRoleMap";
//    private static final String SCHEME_DEVELOPER = "Developer";

    @Bean
    public GlobalOpenApiCustomizer wisepenOpenApiCustomizer(WisepenOpenApiProperties properties, Environment environment) {
        return openApi -> {
            applyInfo(openApi, properties, environment);
            applySecuritySchemes(openApi);
        };
    }

    @Bean
    public GlobalOperationCustomizer wisepenOperationCustomizer() {
        return (operation, handlerMethod) -> {
            // SecurityRequirement 表示某一个接口需要满足的一组安全要求
            SecurityRequirement securityRequirement = new SecurityRequirement();

            // 大多数接口都需要 FromSource 用于证明请求来自可信网关（在使用代理时被暂时移除）
            // securityRequirement.addList(SCHEME_FROM_SOURCE);

            // 判断当前接口是否需要登录上下文
            if (requiresLoginContext(handlerMethod)) {
                securityRequirement.addList(SCHEME_USER_ID);
            }

            // 判断当前接口是否需要用户身份上下文
            if (requiresRoleContext(handlerMethod)) {
                securityRequirement.addList(SCHEME_IDENTITY_TYPE);
                securityRequirement.addList(SCHEME_USER_STATUS);
                securityRequirement.addList(SCHEME_GROUP_ROLE_MAP);
            }

            if (!securityRequirement.isEmpty()) {
                addSecurityRequirement(operation, securityRequirement);
            }

            return operation;
        };
    }

    private void applyInfo(OpenAPI openApi, WisepenOpenApiProperties properties, Environment environment) {
        Info info = openApi.getInfo() == null ? new Info() : openApi.getInfo();

        String title = StringUtils.hasText(properties.getTitle()) ?
                properties.getTitle() :
                environment.getProperty("spring.application.name", "WisePen API");
        info.setTitle(title);

        String version = StringUtils.hasText(properties.getVersion()) ? properties.getVersion() : "1.0.0";
        info.setVersion(version);

        if (StringUtils.hasText(properties.getDescription())) {
            info.setDescription(properties.getDescription());
        }

        openApi.setInfo(info);
    }

    private void applySecuritySchemes(OpenAPI openApi) {
        Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
        /*
        // 下列安全头在使用代理时被暂时移除
        components.addSecuritySchemes(SCHEME_FROM_SOURCE, headerScheme(
                SecurityConstants.HEADER_FROM_SOURCE,
                "内部来源校验头。直连微服务测试时填写 APISIX 注入的固定来源值。"
        ));
        components.addSecuritySchemes(SCHEME_DEVELOPER, headerScheme(
                CommonConstants.GRAY_HEADER_DEV_KEY,
                "灰度隔离开发者标识。通常由 dev.properties 设置；直连测试时可手工透传。"
        ));
        */
        components.addSecuritySchemes(SCHEME_USER_ID, headerScheme(
                SecurityConstants.HEADER_USER_ID,
                "当前用户 ID。直连微服务测试 @CheckLogin / @CheckRole 接口时手工模拟 APISIX 注入。"
        ));
        components.addSecuritySchemes(SCHEME_IDENTITY_TYPE, headerScheme(
                SecurityConstants.HEADER_IDENTITY_TYPE,
                "当前用户身份类型：1=学生，2=教师，3=管理员。"
        ));
        components.addSecuritySchemes(SCHEME_USER_STATUS, headerScheme(
                SecurityConstants.HEADER_USER_STATUS,
                "当前用户账号状态：1=正常，-1=未完成身份认证，-2=封禁。"
        ));
        components.addSecuritySchemes(SCHEME_GROUP_ROLE_MAP, headerScheme(
                SecurityConstants.HEADER_GROUP_ROLE_MAP,
                "当前用户小组角色 JSON，例如 {\"10001\":0}，角色值：0=OWNER，1=ADMIN，2=MEMBER。"
        ));
        openApi.setComponents(components);
    }

    private SecurityScheme headerScheme(String headerName, String description) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(headerName)
                .description(description);
    }

    // 判断当前接口是否需要登录上下文
    private boolean requiresLoginContext(HandlerMethod handlerMethod) {
        return hasAnnotation(handlerMethod, CheckLogin.class) || hasAnnotation(handlerMethod, CheckRole.class);
    }

    // 判断当前接口是否需要身份上下文
    private boolean requiresRoleContext(HandlerMethod handlerMethod) {
        return hasAnnotation(handlerMethod, CheckRole.class);
    }

    // 判断指定注解是否存在于 Controller 方法或 Controller 类上
    private boolean hasAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();
        return AnnotatedElementUtils.hasAnnotation(method, annotationType)
                || AnnotatedElementUtils.hasAnnotation(beanType, annotationType);
    }

    // 把安全要求追加到当前接口的 OpenAPI Operation 上
    private void addSecurityRequirement(Operation operation, SecurityRequirement securityRequirement) {
        if (operation.getSecurity() == null) {
            operation.setSecurity(new ArrayList<>());
        }
        operation.getSecurity().add(securityRequirement);
    }
}
