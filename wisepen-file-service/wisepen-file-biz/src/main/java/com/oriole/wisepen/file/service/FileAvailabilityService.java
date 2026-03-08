package com.oriole.wisepen.file.service;

import com.oriole.wisepen.file.domain.entity.FileInfo;

/**
 * 文件可用性服务接口 - 统一管理"文件变为 AVAILABLE"的状态转换
 *
 * @author Ian.xiong
 */
public interface FileAvailabilityService {

    /**
     * 将指定文件记录的状态更新为 AVAILABLE，并向 resource-service 注册资源摘要。
     *
     * @param update   携带要更新字段（id、updateTime、status、pdfUrl 等）的 FileInfo 对象
     * @param fileInfo 完整的原始 FileInfo，用于提取注册资源所需的元信息
     */
    void markAvailableAndRegister(FileInfo update, FileInfo fileInfo);

    /**
     * 文件已经 insert 为 AVAILABLE（如秒传），只需补充注册资源。
     *
     * @param fileInfo 已持久化的完整 FileInfo
     */
    void registerResource(FileInfo fileInfo);
}
