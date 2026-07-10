package com.oriole.wisepen.common.gray;

import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GrayFacade {

    private GrayFacade() {
    }

    public static String extractDeveloperTag(HttpServletRequest request) {
        return normalizeDeveloperTag(request.getHeader(CommonConstants.GRAY_HEADER_DEV_KEY));
    }

    public static String normalizeDeveloperTag(String developer) {
        if (!StringUtils.hasText(developer)) {
            return null;
        }
        return developer.trim();
    }

    public static void applyInboundDeveloperTag(HttpServletRequest request) {
        String developer = extractDeveloperTag(request);
        if (StringUtils.hasText(developer)) {
            GrayContextHolder.setDeveloperTag(developer);
        }
    }

    public static void applyOutboundDeveloperHeader(RequestTemplate template) {
        String developer = GrayContextHolder.getDeveloperTag();
        if (StringUtils.hasText(developer)) {
            template.header(CommonConstants.GRAY_HEADER_DEV_KEY, developer);
        }
    }

    public static void applyNacosMetadata(Map<String, String> metadata, String developer) {
        String normalized = normalizeDeveloperTag(developer);
        if (StringUtils.hasText(normalized)) {
            metadata.put(CommonConstants.GRAY_METADATA_DEV_KEY, normalized);
            GrayContextHolder.setProcessDefaultTag(normalized);
        }
    }

    public static List<ServiceInstance> selectGrayInstances(List<ServiceInstance> instances) {
        String targetDeveloper = GrayContextHolder.getDeveloperTag();
        if (!StringUtils.hasText(targetDeveloper)) {
            return getStableInstances(instances);
        }

        List<ServiceInstance> targetInstances = instances.stream()
                .filter(instance -> targetDeveloper.equals(instance.getMetadata().get(CommonConstants.GRAY_METADATA_DEV_KEY)))
                .collect(Collectors.toList());

        return targetInstances.isEmpty() ? getStableInstances(instances) : targetInstances;
    }

    private static List<ServiceInstance> getStableInstances(List<ServiceInstance> instances) {
        return instances.stream()
                .filter(instance -> !instance.getMetadata().containsKey(CommonConstants.GRAY_METADATA_DEV_KEY))
                .collect(Collectors.toList());
    }
}
