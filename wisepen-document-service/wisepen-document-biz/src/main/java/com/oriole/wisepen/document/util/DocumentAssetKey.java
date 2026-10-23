package com.oriole.wisepen.document.util;

import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;

/**
 * 文档版本目录内正文图片的命名规约。objectKey 对存储服务保持不透明，
 * 存储侧不做格式校验；本域的链接编解码与序号解析依赖此约定。
 */
public final class DocumentAssetKey {

    private static final String ROOT = StorageSceneEnum.PRIVATE_DOC.getPrefix() + "/";
    private static final String ASSETS = "assets/images";

    private DocumentAssetKey() {
    }

    /** 上传/复制时的业务目录：{documentId}/assets/images */
    public static String assetsBizTag(String documentId) {
        return documentId + "/" + ASSETS;
    }

    /** 删除/列举时的目录前缀：private/docs/{documentId}/assets/images/ */
    public static String assetPrefix(String documentId) {
        return ROOT + assetsBizTag(documentId) + "/";
    }

    /** 固定文件名：image-%04d.{ext} */
    public static String fileName(int index, String extension) {
        return String.format("image-%04d.%s", index, extension);
    }

    /** 从 objectKey 解析图片序号；不符合约定时返回 -1。 */
    public static int imageIndex(String objectKey) {
        if (objectKey == null) return -1;
        int slash = objectKey.lastIndexOf('/');
        String name = slash < 0 ? objectKey : objectKey.substring(slash + 1);
        if (!name.startsWith("image-")) return -1;
        int dot = name.lastIndexOf('.');
        if (dot <= "image-".length()) return -1;
        try {
            return Integer.parseInt(name.substring("image-".length(), dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
