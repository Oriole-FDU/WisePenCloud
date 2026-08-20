package com.oriole.wisepen.document.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(contextId = "remoteDocumentService", value = "wisepen-document-service")
public interface RemoteDocumentService {

    @GetMapping("/internal/document/getDocumentSearchText")
    R<DocumentSearchTextResponse> getDocumentSearchText(@RequestParam("resourceId") String resourceId,
                                                        @RequestParam(value = "version", required = false) Integer version);
}
