package com.oriole.wisepen.file.storage.api.util;

import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;

import java.util.regex.Pattern;

/** 文档域与存储域共用的目录规约，documentId 对应原文档的一个版本。 */
public final class DocumentAssetKey {

    private static final String DOCUMENT_ID = "[A-Za-z0-9-]+";
    private static final String EXTENSION = "(?:png|jpg|jpeg|gif|bmp|tif|tiff|webp)";
    private static final String ROOT = StorageSceneEnum.PRIVATE_DOC_ASSET.getPrefix() + "/";
    private static final Pattern IMAGE = Pattern.compile(ROOT + DOCUMENT_ID + "/assets/images/image-[0-9]{4,9}\\." + EXTENSION);
    private static final Pattern PREFIX = Pattern.compile(ROOT + DOCUMENT_ID + "/");

    private DocumentAssetKey() {
    }

    public static String prefix(String documentId) {
        String prefix = ROOT + documentId + "/";
        if (documentId == null || !PREFIX.matcher(prefix).matches()) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID, "文档版本目录无效");
        }
        return prefix;
    }

    public static boolean isDocumentPrefix(String prefix) {
        return prefix != null && PREFIX.matcher(prefix).matches();
    }

    public static boolean isImage(String key) {
        return key != null && IMAGE.matcher(key).matches();
    }

    public static String build(String bizTag, String fileName) {
        String key = ROOT + bizTag + "/" + fileName;
        if (!isImage(key)) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID, "文档图片目录或文件名无效");
        }
        return key;
    }
}
