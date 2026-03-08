package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadResult;
import com.oriole.wisepen.file.api.domain.dto.FileUploadRequest;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
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
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());
        FileUploadResult response = fileService.upload(file, uploadRequest, userId);
        return R.ok(response);
    }

    /**
     * 获取当前用户的文件列表
     */
    @GetMapping("/list")
    public R<PageResult<FileInfoVO>> getMyFileList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());
        return R.ok(fileService.getMyFileList(page, size, userId));
    }
    /**
     * 重命名文件（同步更新资源服务）
     */
    @PostMapping("/rename/{id}")
    public R<Void> renameFile(@PathVariable Long id, @RequestParam("name") String name) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());
        fileService.renameFile(id, name, userId);
        return R.ok();
    }

    /**
     * 删除文件（同步更新资源服务）
     */
    @DeleteMapping("/delete/{id}")
    public R<Void> deleteFile(@PathVariable Long id) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());
        fileService.deleteFile(id, userId);
        return R.ok();
    }
}