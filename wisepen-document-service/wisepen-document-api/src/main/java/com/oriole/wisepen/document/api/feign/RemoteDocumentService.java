package com.oriole.wisepen.document.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageReferencesResponse;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentImageResolveResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(contextId = "remoteDocumentService", value = "wisepen-document-service")
public interface RemoteDocumentService {

    /** 调用方须在可信内部边界完成具体版本的 VIEW 授权。 */
    @GetMapping("/internal/document/getDocumentImageReferences")
    R<DocumentImageReferencesResponse> getDocumentImageReferences(@RequestParam("resourceId") String resourceId,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "pageNo", required = false) Integer pageNo);

    @GetMapping("/internal/document/resolveDocumentImage")
    R<DocumentImageResolveResponse> resolveDocumentImage(@RequestParam("resourceId") String resourceId,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam("assetRef") String assetRef);

    @GetMapping("/internal/document/getDocumentSearchText")
    R<DocumentSearchTextResponse> getDocumentSearchText(@RequestParam("resourceId") String resourceId,
                                                        @RequestParam(value = "version", required = false) Integer version);
}
