package com.oriole.wisepen.file.service;

import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadResult;
import com.oriole.wisepen.file.api.domain.dto.FileUploadRequest;
import org.springframework.web.multipart.MultipartFile;
import com.oriole.wisepen.common.core.domain.PageResult;
/**
 * 文件服务接口
 *
 * @author Ian.xiong
 */
public interface FileService {

    /**
     * 文件上传（含秒传逻辑）
     */
    FileUploadResult upload(MultipartFile file, FileUploadRequest uploadRequest, Long userId);

    /**
     * 获取当前用户的文件列表（按上传时间倒序）
     */
    PageResult<FileInfoVO> getMyFileList(int page, int size, Long userId);

    /**
     * 删除文件（校验所有权）
     */
    void deleteFile(Long fileId, Long userId);

    /**
     * 重命名文件
     */
    void renameFile(Long fileId, String newName, Long userId);
}
