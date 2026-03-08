package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadResult;
import com.oriole.wisepen.file.api.domain.dto.FileUploadRequest;

import com.oriole.wisepen.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理接口（对外开放）
 *
 * @author Ian.xiong
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 文件上传（含秒传逻辑）
     */
    @PostMapping("/upload")
    public R<FileUploadResult> upload(@RequestPart("file") MultipartFile file,
                                      @Valid @RequestPart("data") FileUploadRequest uploadRequest) {
        FileUploadResult response = fileService.upload(file, uploadRequest);
        return R.ok(response);
    }

    /**
     * 获取当前用户的文件列表
     */
    @GetMapping("/list")
    public R<PageResult<FileInfoVO>> getMyFileList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(fileService.getMyFileList(page, size));
    }
}