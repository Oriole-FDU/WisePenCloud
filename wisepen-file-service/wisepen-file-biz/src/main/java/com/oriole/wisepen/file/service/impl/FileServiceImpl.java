package com.oriole.wisepen.file.service.impl;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.exception.FileErrorCode;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.api.domain.dto.*;
import com.oriole.wisepen.file.api.domain.dto.FileUploadResult;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import com.oriole.wisepen.file.service.FileService;
import com.oriole.wisepen.file.service.FileAvailabilityService;
import com.oriole.wisepen.file.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.oriole.wisepen.file.config.FileProperties;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件存储服务实现类
 *
 * @author Ian.xiong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileProperties fileProperties;
    private final FileAvailabilityService fileAvailabilityService;
    private final com.oriole.wisepen.resource.feign.RemoteResourceService remoteResourceService;

    // ==================== 上传 ====================



    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResult upload(MultipartFile file, FileUploadRequest uploadRequest) {
        String originalFilename = uploadRequest.getFilename();
        String extension = cn.hutool.core.io.FileUtil.extName(originalFilename);
        log.info("Uploading file: {}, MD5: {}, declared size: {} bytes, actual size: {} bytes", 
                originalFilename, uploadRequest.getMd5(), uploadRequest.getFileSize(), file.getSize());

        // 1. 文件校验
        FileValidator.validateFileSize(file);
        FileValidator.validateFileType(file, extension);
        
        if (uploadRequest.getFileSize() != null && !Long.valueOf(file.getSize()).equals(uploadRequest.getFileSize())) {
            log.warn("File size mismatch! Declared: {}, Actual: {}", uploadRequest.getFileSize(), file.getSize());
            throw new ServiceException(FileErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 为避免流被重复读取或消耗，先将其写入本地缓存，再对本地 File 计算 MD5
        String uuId = UUID.randomUUID().toString();
        String localCachePath = uploadCache(file, extension, uuId);
        java.io.File cachedFile = new java.io.File(localCachePath);

        // 2. 服务端 MD5 校验（防伪造哈希）
        String serverMd5 = FileValidator.calculateMd5(cachedFile);
        if (!serverMd5.equalsIgnoreCase(uploadRequest.getMd5())) {
            log.warn("MD5 mismatch! Client: {}, Server: {}", uploadRequest.getMd5(), serverMd5);
            // 删除垃圾缓存文件
            cn.hutool.core.io.FileUtil.del(cachedFile);
            throw new ServiceException(FileErrorCode.FILE_MD5_MISMATCH);
        }

        // 3. 秒传逻辑：仅匹配 status=AVAILABLE 的记录
        FileInfo existingFile = fileMapper.selectOne(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getMd5, serverMd5)
                .eq(FileInfo::getStatus, FileConstants.UPLOAD_STATUS_AVAILABLE)
                .last("LIMIT 1"));

        Long userId = Long.parseLong(SecurityContextHolder.getUserId());

        if (existingFile != null) {
            // 秒传：拷贝 url + pdfUrl，创建全新记录
            log.info("Flash upload triggered for MD5: {}", serverMd5);
            cn.hutool.core.io.FileUtil.del(cachedFile); // 既然已经有存档，当前的物理缓存直接删去

            FileInfo newRecord = new FileInfo();
            newRecord.setFilename(originalFilename);
            newRecord.setMd5(serverMd5);
            newRecord.setType(extension);
            newRecord.setSize(file.getSize());
            newRecord.setUrl(existingFile.getUrl());
            newRecord.setPdfUrl(existingFile.getPdfUrl());
            newRecord.setCreateBy(userId);
            newRecord.setStatus(FileConstants.UPLOAD_STATUS_AVAILABLE);
            fileMapper.insert(newRecord);
            // 秒传直接 AVAILABLE，触发统一资源注册
            fileAvailabilityService.registerResource(newRecord);
            FileUploadResult result = new FileUploadResult();
            result.setDocumentId(newRecord.getId());
            result.setFilename(newRecord.getFilename());
            return result;
        }

        // 4. 正常落盘
        // 生成 ObjectKey: yyyy/MM/dd/{uuid}.{ext}
        String datePath = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        String objectKey = datePath + "/" + uuId + "." + extension;

        // 物理存储路径 (Consumer 使用)

        String accessUrl;
        if (fileProperties.getOss().isEnabled()) {
            // OSS 公网访问 URL: https://bucket.endpoint/objectKey
            String bucket = fileProperties.getOss().getBucketName();
            String endpoint = fileProperties.getOss().getEndpoint();
            accessUrl = "https://" + bucket + "." + endpoint + "/" + objectKey;
        } else {
            // 本地访问 URL
            accessUrl = formatBasePath(fileProperties.getDomain()) + objectKey;
        }

        boolean isOffice = FileConstants.OFFICE_EXTENSIONS.contains(extension.toLowerCase());
        boolean isPdf = "pdf".equalsIgnoreCase(extension);

        // 所有新文件初始状态都是 PROCESSING（需要异步上传至 OSS）
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFilename(originalFilename);
        fileInfo.setMd5(serverMd5);
        fileInfo.setType(extension);
        fileInfo.setSize(file.getSize());
        // 存入访问链接
        fileInfo.setUrl(accessUrl);
        fileInfo.setCreateBy(userId);
        fileInfo.setStatus(FileConstants.UPLOAD_STATUS_PROCESSING);

        fileMapper.insert(fileInfo);

        // 5. 事务提交后推送 Redis 队列
        final Long fileId = fileInfo.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 推送上传任务
                FileUploadTaskDTO uploadTask = new FileUploadTaskDTO();
                uploadTask.setFileId(fileId);
                uploadTask.setOriginalFilename(originalFilename);
                uploadTask.setTempFilePath(localCachePath);
                uploadTask.setAccessUrl(accessUrl); // 传递 Web URL
                uploadTask.setMd5(serverMd5);
                uploadTask.setIsPdfDirect(isPdf);
                uploadTask.setSize(fileInfo.getSize());
                uploadTask.setCreateBy(userId);
                stringRedisTemplate.opsForList().leftPush(FileConstants.UPLOAD_QUEUE_KEY + ":" + fileProperties.getInstanceId(), JSON.toJSONString(uploadTask));
                log.info("Pushed upload task to Redis for fileId: {}", fileId);

                // Office 文档：额外推送转换任务
                if (isOffice) {
                    FileConvertTaskDTO convertTask = new FileConvertTaskDTO();
                    convertTask.setFileId(fileId);
                    convertTask.setOriginalFilename(originalFilename);
                    convertTask.setExtension(extension);
                    convertTask.setTempFilePath(localCachePath);
                    convertTask.setOriginalSize(file.getSize());
                    convertTask.setMd5(serverMd5);
                    convertTask.setSize(fileInfo.getSize());
                    convertTask.setCreateBy(userId);
                    stringRedisTemplate.opsForList().leftPush(FileConstants.CONVERT_QUEUE_KEY + ":" + fileProperties.getInstanceId(), JSON.toJSONString(convertTask));
                    log.info("Pushed conversion task to Redis for fileId: {}", fileId);
                }
            }
        });

        // 简化返回：仅返回 documentId 和 filename
        FileUploadResult result = new FileUploadResult();
        result.setDocumentId(fileInfo.getId());
        result.setFilename(fileInfo.getFilename());
        return result;
    }

    // ==================== 文件列表 ====================

    @Override
    public PageResult<FileInfoVO> getMyFileList(int page, int size) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());

        Page<FileInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<FileInfo> wrapper = Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getCreateBy, userId)
                .orderByDesc(FileInfo::getCreateTime);

        Page<FileInfo> result = fileMapper.selectPage(pageParam, wrapper);

        List<FileInfoVO> records = result.getRecords().stream()
                .map(this::toFileInfoVO)
                .collect(Collectors.toList());
        
        PageResult<FileInfoVO> pageResult = new PageResult<>(result.getTotal(), page, size);
        pageResult.addAll(records);
        return pageResult;
    }

    // ==================== 删除文件 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long fileId) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());
        FileInfo existingFile = fileMapper.selectById(fileId);
        if (existingFile == null) {
            throw new ServiceException(FileErrorCode.FILE_NOT_FOUND);
        }
        
        // 1. 本地删除的鉴权：如果有关联 resourceId，则交给 resource-service 鉴权
        if (cn.hutool.core.util.StrUtil.isNotBlank(existingFile.getResourceId())) {
            com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionDTO authDto = 
                    new com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionDTO();
            authDto.setResourceId(existingFile.getResourceId());
            authDto.setResourceType(existingFile.getType().toUpperCase());
            authDto.setUserId(userId.toString());
            authDto.setGroupRoles(java.util.Collections.emptyMap()); // 此处如果需要群组权限，可以传入真实群组，暂传空表示仅个人权限
            
            com.oriole.wisepen.common.core.domain.R<Boolean> authResult = remoteResourceService.checkResPermission(authDto);
            if (authResult.getCode() != 200 || Boolean.FALSE.equals(authResult.getData())) {
                log.warn("Permission denied by Resource Service to delete file: {}", fileId);
                throw new ServiceException("当前用户无权删除该资源");
            }

            // 同步删除 resource-service 中的主记录
            com.oriole.wisepen.common.core.domain.R<Void> result = 
                remoteResourceService.removeResource(existingFile.getResourceId());
            if (result.getCode() != 200) {
                log.warn("Failed to delete resource in Resource Service: {}", result.getMsg());
                throw new ServiceException("Failed to delete resource: " + result.getMsg());
            }
        } else {
            // 没有 resourceId（可能由于历史遗留/注册失败），降级为校验文件上传者
            if (!existingFile.getCreateBy().equals(userId)) {
                throw new ServiceException(FileErrorCode.FILE_NOT_FOUND);
            }
        }
        
        // 2. 本地软删除
        fileMapper.deleteById(fileId);
        log.info("File deleted: fileId={}", fileId);
    }

    // ==================== 私有方法 ====================

    private String uploadCache(MultipartFile file, String extension, String uuId) {
        String cacheFilePath = formatBasePath(fileProperties.getCachePath()) + uuId + "." + extension;
        java.io.File dest = new java.io.File(cacheFilePath);
        cn.hutool.core.io.FileUtil.touch(dest);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("Failed to transfer file to cache: {}", cacheFilePath, e);
            throw new ServiceException(FileErrorCode.FILE_UPLOAD_ERROR);
        }
        log.info("Upload to server cache successful, saved to: {}", cacheFilePath);
        return cacheFilePath;
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

    private String formatBasePath(String basePath) {
        if (!basePath.endsWith("/")) {
            return basePath + "/";
        }
        return basePath;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameFile(Long fileId, String name) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());
        FileInfo existingFile = fileMapper.selectById(fileId);
        if (existingFile == null) {
            throw new ServiceException(FileErrorCode.FILE_NOT_FOUND);
        }
        
        // 1. 本地重命名的鉴权：如果有关联 resourceId，则交给 resource-service 鉴权
        if (cn.hutool.core.util.StrUtil.isNotBlank(existingFile.getResourceId())) {
            com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionDTO authDto = 
                    new com.oriole.wisepen.resource.domain.dto.ResourceCheckPermissionDTO();
            authDto.setResourceId(existingFile.getResourceId());
            authDto.setResourceType(existingFile.getType().toUpperCase());
            authDto.setUserId(userId.toString());
            authDto.setGroupRoles(java.util.Collections.emptyMap()); // 暂无群组支持，传空
            
            com.oriole.wisepen.common.core.domain.R<Boolean> authResult = remoteResourceService.checkResPermission(authDto);
            if (authResult.getCode() != 200 || Boolean.FALSE.equals(authResult.getData())) {
                log.warn("Permission denied by Resource Service to rename file: {}", fileId);
                throw new ServiceException("当前用户无权重命名该资源");
            }

            // 同步修改 resource-service 中的记录名
            com.oriole.wisepen.resource.domain.dto.ResourceUpdateDTO updateDTO = new com.oriole.wisepen.resource.domain.dto.ResourceUpdateDTO();
            updateDTO.setResourceId(existingFile.getResourceId());
            updateDTO.setResourceName(name); // 仅更新名字
            com.oriole.wisepen.common.core.domain.R<Void> result = remoteResourceService.updateAttributes(updateDTO);
            if (result.getCode() != 200) {
                log.warn("Failed to rename resource in Resource Service: {}", result.getMsg());
                throw new ServiceException("Failed to rename resource: " + result.getMsg());
            }
        } else {
            // 没有 resourceId（可能由于历史遗留/注册失败），降级为校验文件上传者
            if (!existingFile.getCreateBy().equals(userId)) {
                throw new ServiceException(FileErrorCode.FILE_NOT_FOUND);
            }
        }
        
        // 2. 本地修改名字
        FileInfo update = new FileInfo();
        update.setId(fileId);
        update.setFilename(name);
        update.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(update);
        log.info("File renamed: fileId={}, newName={}", fileId, name);
    }
}
