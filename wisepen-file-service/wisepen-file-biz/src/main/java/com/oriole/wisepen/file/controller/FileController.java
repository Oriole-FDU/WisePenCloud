package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.service.FileService;
import com.oriole.wisepen.file.api.domain.dto.UploadRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public R<Void> upload(@RequestPart("file") MultipartFile file, @Valid @RequestPart("data") UploadRequest uploadRequest) {
        try {
            fileService.upload(file, uploadRequest);
            return R.ok();
        } catch (IOException e) {
            throw new RuntimeException("Upload failed", e);
        }
    }
    @PostMapping("/test")
    public R<Void> test() {
        return R.ok();
    }
}