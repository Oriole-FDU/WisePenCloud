package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadResult;
import com.oriole.wisepen.file.api.domain.dto.UploadRequest;

import com.oriole.wisepen.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件管理接口
 *
 * @author Ian.Xiong
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
                                    @Valid @RequestPart("data") UploadRequest uploadRequest) {
        try {
            FileUploadResult response = fileService.upload(file, uploadRequest);
            return R.ok(response);
        } catch (IOException e) {
            throw new RuntimeException("Upload failed", e);
        }
    }

    /**
     * 获取当前用户的文件列表
     */
    @GetMapping("/list")
    public R<List<FileInfoVO>> getMyFileList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(fileService.getMyFileList(page, size));
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return R.ok();
    }
}