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
 * @author Ian.Xiong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileMapper fileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileProperties fileProperties;

    // ==================== 上传 ====================



    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResult upload(MultipartFile file, UploadRequest uploadRequest) throws IOException {
        String originalFilename = uploadRequest.getFilename();
        String extension = cn.hutool.core.io.FileUtil.extName(originalFilename);
        log.info("Uploading file: {}, MD5: {}, size: {} bytes", originalFilename, uploadRequest.getMd5(), file.getSize());

        // 1. 文件校验
        FileValidator.validateFileSize(file);
        FileValidator.validateFileType(file, extension);

        // 2. 服务端 MD5 校验（防伪造哈希）
        String serverMd5 = FileValidator.calculateMd5(file);
        if (!serverMd5.equalsIgnoreCase(uploadRequest.getMd5())) {
            log.warn("MD5 mismatch! Client: {}, Server: {}", uploadRequest.getMd5(), serverMd5);
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
            FileInfo newRecord = FileInfo.builder()
                    .filename(originalFilename)
                    .md5(serverMd5)
                    .type(extension)
                    .size(file.getSize())
                    .url(existingFile.getUrl())
                    .pdfUrl(existingFile.getPdfUrl())
                    .createBy(userId)
                    .status(FileConstants.UPLOAD_STATUS_AVAILABLE)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            fileMapper.insert(newRecord);
            return FileUploadResult.builder()
                    .documentId(newRecord.getId())
                    .filename(newRecord.getFilename())
                    .build();
        }

        // 4. 正常落盘
        String uuId = UUID.randomUUID().toString();
        // 生成 ObjectKey: yyyy/MM/dd/{uuid}.{ext}
        String datePath = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        String objectKey = datePath + "/" + uuId + "." + extension;

        String localCachePath = uploadCache(file, extension, uuId);

        // 物理存储路径 (Consumer 使用)
        String storagePath = fileProperties.getStoragePath();
        if (!storagePath.endsWith("/")) {
            storagePath += "/";
        }
        String finalFilePath = storagePath + objectKey;

        // 公网访问 URL (存入数据库)
        String domain = fileProperties.getDomain();
        if (!domain.endsWith("/")) {
            domain += "/";
        }
        String accessUrl = domain + objectKey;

        boolean isOffice = FileConstants.OFFICE_EXTENSIONS.contains(extension.toLowerCase());
        boolean isPdf = "pdf".equalsIgnoreCase(extension);

        // 所有新文件初始状态都是 PROCESSING（需要异步上传至 OSS）
        FileInfo fileInfo = FileInfo.builder()
                .filename(originalFilename)
                .md5(serverMd5)
                .type(extension)
                .size(file.getSize())
                .url(accessUrl) // 存入访问链接
                .createBy(userId)
                .status(FileConstants.UPLOAD_STATUS_PROCESSING)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        fileMapper.insert(fileInfo);

        // 5. 事务提交后推送 Redis 队列
        final Long fileId = fileInfo.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 推送上传任务
                FileUploadTaskDTO uploadTask = FileUploadTaskDTO.builder()
                        .fileId(fileId)
                        .originalFilename(originalFilename)
                        .tempFilePath(localCachePath)
                        .accessUrl(accessUrl) // 传递 Web URL
                        .md5(serverMd5)
                        .isPdfDirect(isPdf)
                        .build();
                stringRedisTemplate.opsForList().leftPush(FileConstants.UPLOAD_QUEUE_KEY + ":" + fileProperties.getInstanceId(), JSON.toJSONString(uploadTask));
                log.info("Pushed upload task to Redis for fileId: {}", fileId);

                // Office 文档：额外推送转换任务
                if (isOffice) {
                    FileConvertTaskDTO convertTask = FileConvertTaskDTO.builder()
                            .fileId(fileId)
                            .originalFilename(originalFilename)
                            .extension(extension)
                            .tempFilePath(localCachePath)
                            .originalSize(file.getSize())
                            .md5(serverMd5)
                            .build();
                    stringRedisTemplate.opsForList().leftPush(FileConstants.CONVERT_QUEUE_KEY + ":" + fileProperties.getInstanceId(), JSON.toJSONString(convertTask));
                    log.info("Pushed conversion task to Redis for fileId: {}", fileId);
                }
            }
        });

        // 简化返回：仅返回 documentId 和 filename
        return FileUploadResult.builder()
                .documentId(fileInfo.getId())
                .filename(fileInfo.getFilename())
                .build();
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

        FileInfo fileInfo = fileMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new ServiceException(FileErrorCode.FILE_NOT_FOUND);
        }

        // 越权防御：createdBy 必须匹配当前用户
        if (!userId.equals(fileInfo.getCreateBy())) {
            throw new ServiceException(FileErrorCode.FILE_OPERATION_FORBIDDEN);
        }

        fileMapper.deleteById(fileId);
        log.info("File deleted: fileId={}, userId={}", fileId, userId);
    }

    // ==================== 私有方法 ====================

    private String uploadCache(MultipartFile file, String extension, String uuId) throws IOException {
        String cacheFilePath = "/tmp/wisepen/upload/cache/" + uuId + "." + extension;
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
        return FileInfoVO.builder()
                .documentId(fileInfo.getId())
                .fileName(fileInfo.getFilename())
                .fileSize(fileInfo.getSize())
                .createTime(fileInfo.getCreateTime())
                .status(fileInfo.getStatus())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameFile(Long fileId, String name) {
        Long userId = Long.parseLong(SecurityContextHolder.getUserId());

        FileInfo fileInfo = fileMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new ServiceException(FileErrorCode.FILE_NOT_FOUND);
        }

        // 越权防御：createdBy 必须匹配当前用户
        if (!userId.equals(fileInfo.getCreateBy())) {
            throw new ServiceException(FileErrorCode.FILE_OPERATION_FORBIDDEN);
        }

        fileInfo.setFilename(name);
        fileInfo.setUpdateTime(LocalDateTime.now());
        fileMapper.updateById(fileInfo);
        log.info("File renamed: fileId={}, userId={}, newName={}", fileId, userId, name);
    }
}
