package com.oriole.wisepen.document.util;

import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.util.DocumentAssetKey;

/** 平台链接只封装 objectKey，不携带域名、签名或独立资产 ID。 */
public final class MarkdownAssetLinkCodec {

    public static final String SCHEME = "wisepen-asset:///";

    private MarkdownAssetLinkCodec() {
    }

    public static String link(String objectKey) {
        if (!DocumentAssetKey.isImage(objectKey)) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID, "文档图片 key 无效");
        }
        return SCHEME + objectKey;
    }

    public static String objectKey(String assetRef, String documentId) {
        String key = assetRef != null && assetRef.startsWith(SCHEME) ? assetRef.substring(SCHEME.length()) : assetRef;
        // 仅接受规范 ASCII key，编码分隔符、query、fragment、authority 都无法通过目录校验。
        if (!DocumentAssetKey.isImage(key)
                || !key.startsWith(DocumentAssetKey.prefix(documentId) + "assets/")) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID, "文档图片链接或版本归属无效");
        }
        return key;
    }
}
