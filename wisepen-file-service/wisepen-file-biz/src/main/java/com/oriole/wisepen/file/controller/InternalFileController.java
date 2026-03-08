package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文件内部接口（仅供微服务内部调用，网关应屏蔽 /internal/** 外网访问）
 *
 * @author Ian.xiong
 */
@RestController
@RequestMapping("/internal/file")
@RequiredArgsConstructor
public class InternalFileController {

    private final FileService fileService;

    /**
     * 重命名文件（由 resource-service 在用户重命名资源后同步调用（保留））
     */
    @PostMapping("/rename/{id}")
    public R<Void> renameFile(@PathVariable Long id, @RequestParam("name") String name) {
        fileService.renameFile(id, name);
        return R.ok();
    }

    /**
     * 删除文件（由上层业务服务调用，校验所有权）
     */
    @DeleteMapping("/delete/{id}")
    public R<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return R.ok();
    }
}
