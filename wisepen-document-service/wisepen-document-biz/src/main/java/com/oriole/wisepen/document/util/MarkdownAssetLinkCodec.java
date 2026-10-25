package com.oriole.wisepen.document.util;

import com.oriole.wisepen.common.core.constant.CommonError;
import com.oriole.wisepen.common.core.exception.ServiceException;

/** 平台链接只封装 objectKey，不携带域名、签名或独立资产 ID。 */
public final class MarkdownAssetLinkCodec {

    public static final String SCHEME = "wisepen-asset:///";

    private MarkdownAssetLinkCodec() {
    }

    public static String link(String objectKey) {
        return SCHEME + objectKey;
    }

    public static String objectKey(String assetRef, String documentId) {
        String key = assetRef != null && assetRef.startsWith(SCHEME) ? assetRef.substring(SCHEME.length()) : assetRef;
        // 仅校验目录归属；该图片是否真实被本版本正文引用，由调用方按正文匹配保证。
        if (key == null || !key.startsWith(DocumentAssetKey.assetPrefix(documentId))) {
            throw new ServiceException(CommonError.REQUEST_PARAM_INVALID, "文档图片链接或版本归属无效");
        }
        return key;
    }
}
