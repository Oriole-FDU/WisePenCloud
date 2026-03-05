package com.oriole.wisepen.file.service;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateDTO;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件可用性服务 - 统一管理"文件变为 AVAILABLE"的状态转换
 * <p>
 * 所有上传路径（秒传、正常上传、PDF直传等）在文件变为可用时，
 * 均通过此服务完成状态更新 + 资源注册，保证注册逻辑的唯一性。
 * <p>
 * 注意：Office 转换出的 PDF 副本（isConvertedPdf=true）不走此方法，
 * 其状态更新由调用方直接操作 fileMapper，不触发资源注册。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAvailabilityService {

    private final FileMapper fileMapper;
    private final RemoteResourceService remoteResourceService;

    /**
     * 将指定文件记录的状态更新为 AVAILABLE，并向 resource-service 注册资源摘要。
     *
     * @param update   携带要更新字段（id、updateTime、status、pdfUrl 等）的 FileInfo 对象
     * @param fileInfo 完整的原始 FileInfo，用于提取注册资源所需的元信息
     */
    public void markAvailableAndRegister(FileInfo update, FileInfo fileInfo) {
        update.setStatus(FileConstants.UPLOAD_STATUS_AVAILABLE);
        fileMapper.updateById(update);
        registerResource(fileInfo);
    }

    /**
     * 文件已经 insert 为 AVAILABLE（如秒传），只需补充注册资源。
     *
     * @param fileInfo 已持久化的完整 FileInfo
     */
    public void registerResource(FileInfo fileInfo) {
        try {
            ResourceCreateDTO dto = new ResourceCreateDTO();
            dto.setResourceName(fileInfo.getFilename());
            dto.setResourceType(fileInfo.getType().toUpperCase());
            dto.setOwnerId(String.valueOf(fileInfo.getCreateBy()));
            dto.setSize(fileInfo.getSize());

            R<String> result = remoteResourceService.createResource(dto);
            log.info("资源已注册: fileId={}, resourceId={}", fileInfo.getId(), result.getData());
        } catch (Exception e) {
            log.warn("资源注册失败: fileId={}, reason={}", fileInfo.getId(), e.getMessage());
        }
    }
}
