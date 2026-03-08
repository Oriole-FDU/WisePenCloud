package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.exception.FileErrorCode;
import com.oriole.wisepen.file.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ian.xiong
 */
@RestController
@RequestMapping("/remote/file")
@RequiredArgsConstructor
public class RemoteFileController {

    private final FileMapper fileMapper;

    @GetMapping("/info/{fileId}")
    public R<FileInfoVO> getFileInfo(@PathVariable Long fileId) {
        FileInfo fileInfo = fileMapper.selectById(fileId);
        if (fileInfo == null) {
            return R.fail(FileErrorCode.FILE_NOT_FOUND);
        }
        return R.ok(toFileInfoVO(fileInfo));
    }

    private FileInfoVO toFileInfoVO(FileInfo fileInfo) {
        FileInfoVO vo = new FileInfoVO();
        cn.hutool.core.bean.BeanUtil.copyProperties(fileInfo, vo, cn.hutool.core.bean.copier.CopyOptions.create()
                .setFieldMapping(java.util.Map.of(
                        "id", "documentId",
                        "filename", "fileName",
                        "size", "fileSize"
                )));
        return vo;
    }
}
