package com.oriole.wisepen.file.service.impl;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import com.oriole.wisepen.file.service.FileAvailabilityService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateDTO;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件可用性服务实现类 - 统一管理"文件变为 AVAILABLE"的状态转换
 * <p>
 * 所有上传路径（秒传、正常上传、PDF直传等）在文件变为可用时，
 * 均通过此服务完成状态更新 + 资源注册，保证注册逻辑的唯一性。
 * <p>
 * @author Ian.xiong
 * 注意：Office 转换出的 PDF 副本（isConvertedPdf=true）不走此方法，
 * 其状态更新由调用方直接操作 fileMapper，不触发资源注册。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAvailabilityServiceImpl implements FileAvailabilityService {

    private final FileMapper fileMapper;
    private final RemoteResourceService remoteResourceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAvailableAndRegister(FileInfo update, FileInfo fileInfo) {
        update.setStatus(FileConstants.UPLOAD_STATUS_AVAILABLE);
        fileMapper.updateById(update);
        registerResource(fileInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerResource(FileInfo fileInfo) {
        try {
            ResourceCreateDTO dto = new ResourceCreateDTO();
            dto.setResourceName(fileInfo.getFilename());
            dto.setResourceType(fileInfo.getType().toUpperCase());
            dto.setOwnerId(String.valueOf(fileInfo.getCreateBy()));
            dto.setSize(fileInfo.getSize());

            R<String> result = remoteResourceService.createResource(dto);
            String resourceId = result.getData();
            log.info("资源已注册: fileId={}, resourceId={}", fileInfo.getId(), resourceId);

            // 将 resourceId 写回 sys_file，建立 file ↔ resource 的唯一映射
            FileInfo update = new FileInfo();
            update.setId(fileInfo.getId());
            update.setResourceId(resourceId);
            fileMapper.updateById(update);

        } catch (Exception e) {
            log.warn("资源注册失败: fileId={}, reason={}", fileInfo.getId(), e.getMessage());
        }
    }
}
