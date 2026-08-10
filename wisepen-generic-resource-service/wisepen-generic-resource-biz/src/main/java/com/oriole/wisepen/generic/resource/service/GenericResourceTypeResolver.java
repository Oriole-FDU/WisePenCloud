package com.oriole.wisepen.generic.resource.service;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.generic.resource.api.constant.GenericResourceConstants;
import com.oriole.wisepen.generic.resource.exception.GenericResourceError;
import com.oriole.wisepen.resource.enums.ResourceType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class GenericResourceTypeResolver {

    public ResolvedGenericResourceType resolve(String filename, String requestedExtension) {
        String filenameExtension = normalizeRawExtension(resolveExtensionFromFilename(filename));
        String explicitExtension = normalizeRawExtension(requestedExtension);
        // 不能只信任前端传入的 extension，否则 report.docx + extension=zip 会绕过文档服务处理链路。
        if (StringUtils.hasText(filenameExtension) && StringUtils.hasText(explicitExtension)
                && !filenameExtension.equals(explicitExtension)) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
        String extension = StringUtils.hasText(explicitExtension) ? explicitExtension : filenameExtension;
        ResourceType resourceType = StringUtils.hasText(extension)
                ? ResourceType.fromExtension(extension)
                : ResourceType.UNKNOWN;
        if (resourceType == null) {
            resourceType = ResourceType.UNKNOWN;
        }
        if (!GenericResourceConstants.MANAGED_TYPES.contains(resourceType)) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
        return new ResolvedGenericResourceType(resourceType, extension);
    }

    private String normalizeRawExtension(String rawExtension) {
        if (!StringUtils.hasText(rawExtension)) {
            return "";
        }
        String extension = rawExtension.trim().toLowerCase(Locale.ROOT);
        while (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        if (extension.contains("/") || extension.contains("\\") || extension.contains("..")) {
            throw new ServiceException(GenericResourceError.CANNOT_SUPPORT_GENERIC_RESOURCE_TYPE);
        }
        return extension;
    }

    private String resolveExtensionFromFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        String lowerName = filename.trim().toLowerCase(Locale.ROOT);
        // tar.gz 这类复合扩展名不能只截取最后一段，否则会丢失资源类型判断信息。
        for (String extension : ResourceType.compoundExtensions()) {
            if (lowerName.endsWith("." + extension)) {
                return extension;
            }
        }
        int dotIndex = lowerName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == lowerName.length() - 1) {
            return "";
        }
        return lowerName.substring(dotIndex + 1);
    }

    public record ResolvedGenericResourceType(ResourceType resourceType, String extension) {
    }
}
