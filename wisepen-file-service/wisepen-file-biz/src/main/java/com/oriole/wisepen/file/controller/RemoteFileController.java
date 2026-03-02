package com.oriole.wisepen.file.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/remote/file")
@RequiredArgsConstructor
public class RemoteFileController {

    private final FileMapper fileMapper;

    @GetMapping("/info/{fileId}")
    public R<FileInfoVO> getFileInfo(@PathVariable("fileId") Long fileId) {
        FileInfo fileInfo = fileMapper.selectById(fileId);
        if (fileInfo == null) {
            return R.fail("文件不存在");
        }
        return R.ok(toFileInfoVO(fileInfo));
    }

    private FileInfoVO toFileInfoVO(FileInfo fileInfo) {
        return FileInfoVO.builder()
                .documentId(fileInfo.getId())
                .fileName(fileInfo.getFilename())
                .fileSize(fileInfo.getSize())
                .createTime(fileInfo.getCreateTime())
                .status(fileInfo.getStatus())
                .createBy(fileInfo.getCreateBy())
                .build();
    }
}
