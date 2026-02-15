package com.oriole.wisepen.file.service;

import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadResult;
import com.oriole.wisepen.file.api.domain.dto.UploadRequest;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件服务接口
 *
 * @author Ian.Xiong
 */
public interface FileService {

    /**
     * 文件上传（含秒传逻辑）
     */
    FileUploadResult upload(MultipartFile file, UploadRequest uploadRequest) throws IOException;

    /**
     * 获取当前用户的文件列表（按上传时间倒序）
     */
    List<FileInfoVO> getMyFileList(int page, int size);

    /**
     * 删除文件（校验所有权）
     */
    void deleteFile(Long fileId);
}
