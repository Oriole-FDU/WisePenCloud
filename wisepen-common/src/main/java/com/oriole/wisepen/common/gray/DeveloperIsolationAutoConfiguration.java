package com.oriole.wisepen.common.gray;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

@Slf4j
@Configuration
public class DeveloperIsolationAutoConfiguration {

    /**
     * 这个 BeanPostProcessor 会在 Nacos 注册之前运行
     * 它负责拦截 NacosDiscoveryProperties，往里面塞 metadata
     */
    @Bean
    public BeanPostProcessor nacosMetadataInjector() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                // 拦截 Nacos 的配置 Bean
                if (bean instanceof NacosDiscoveryProperties) {
                    injectMetadata((NacosDiscoveryProperties) bean);
                }
                return bean;
            }
        };
    }

    private void injectMetadata(NacosDiscoveryProperties nacosProps) {
        // 尝试读取本地配置文件（位置在项目根目录）
        File devFile = new File(System.getProperty("user.dir"), "dev.properties");

        if (!devFile.exists()) {
            // 也可以读取 IDEA 传入的 VM Options: -Dwisepen.developer.name=
            String developer = System.getProperty("wisepen.developer.name");
            if (StringUtils.hasText(developer)) {
                nacosProps.getMetadata().clear();
                nacosProps.getMetadata().put(CommonConstants.GRAY_METADATA_DEV_KEY, developer);
                GrayContextHolder.setProcessDefaultTag(developer);
                log.warn("Development isolation is now active. Please include the X-Developer header in your requests.");
            }
            return;
        }

        // 如果文件存在，读取配置
        try (FileInputStream fis = new FileInputStream(devFile)) {
            Properties props = new Properties();
            props.load(fis);

            String developer = props.getProperty("wisepen.developer.name");
            String enable = props.getProperty("wisepen.developer.enable");

            if ("true".equalsIgnoreCase(enable) && StringUtils.hasText(developer)) {
                nacosProps.getMetadata().put(CommonConstants.GRAY_METADATA_DEV_KEY, developer);
                GrayContextHolder.setProcessDefaultTag(developer);
                log.warn("Development isolation is now active. Please include the X-Developer header in your requests.");
            }
        } catch (Exception e) {
            log.error("Development isolation failed", e);
        }
    }
}