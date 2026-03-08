package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.domain.result.FileInfoResult;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.exception.FileErrorCode;
import com.oriole.wisepen.file.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;

/**
 * @author Ian.xiong
 */
@RestController
@RequestMapping("/remote/file")
@RequiredArgsConstructor
public class RemoteFileController {

    private final FileMapper fileMapper;

    @GetMapping("/info/{fileId}")
    public R<FileInfoResult> getFileInfo(@PathVariable Long fileId) {
        FileInfo fileInfo = fileMapper.selectById(fileId);
        if (fileInfo == null) {
            return R.fail(FileErrorCode.FILE_NOT_FOUND);
        }
        return R.ok(toFileInfoVO(fileInfo));
    }

    private FileInfoResult toFileInfoVO(FileInfo fileInfo) {
        FileInfoResult vo = new FileInfoResult();
        BeanUtil.copyProperties(fileInfo, vo, CopyOptions.create()
                .setFieldMapping(Map.of(
                        "id", "documentId",
                        "filename", "fileName",
                        "size", "fileSize"
                )));
        return vo;
    }
}
