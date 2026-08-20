package com.oriole.wisepen.document.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import com.oriole.wisepen.document.api.feign.RemoteDocumentService;
import com.oriole.wisepen.document.service.IDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/document")
@RequiredArgsConstructor
public class InternalDocumentController implements RemoteDocumentService {

    private final IDocumentService documentService;

    @GetMapping("/getDocumentSearchText")
    @Override
    public R<DocumentSearchTextResponse> getDocumentSearchText(@RequestParam("resourceId") String resourceId,
                                                               @RequestParam(value = "version", required = false) Integer version) {
        return R.ok(documentService.getDocumentSearchText(resourceId, version));
    }
}
