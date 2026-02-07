package com.oriole.wisepen.file.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.exception.FileErrorCode;
import com.oriole.wisepen.file.api.constant.FileConstants;
import com.oriole.wisepen.file.api.domain.dto.FileConvertTaskDTO;
import com.oriole.wisepen.file.api.domain.dto.FileUploadTaskDTO;
import com.oriole.wisepen.file.api.domain.dto.UploadRequest;
import com.oriole.wisepen.file.domain.entity.FileInfo;
import com.oriole.wisepen.file.mapper.FileMapper;
import com.oriole.wisepen.file.service.FileService;
import com.oriole.wisepen.file.service.OfficeConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 文件存储服务实现类
 *
 * @author Ian.Xiong
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final OfficeConversionService officeConversionService;
    private final FileMapper fileMapper;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upload(MultipartFile file, UploadRequest uploadRequest) throws IOException {
        log.info("Uploading file: {}, MD5: {}", uploadRequest.getFilename(), uploadRequest.getMd5());

        String originalFilename = uploadRequest.getFilename();
        String extension = cn.hutool.core.io.FileUtil.extName(originalFilename);
        String finalFilePath;
        String existingPdfUrl = null;
        String localCachePath = null;
        Integer initialStatus;

        // 1. 秒传逻辑
        FileInfo existingFile = fileMapper.selectOne(Wrappers.<FileInfo>lambdaQuery()
                .eq(FileInfo::getMd5, uploadRequest.getMd5())
                .last("LIMIT 1"));

        boolean isOffice = isOfficeDocument(extension);

        if (existingFile != null) {
            log.info("File with MD5 {} already exists, skipping physical upload (Flash Upload).", uploadRequest.getMd5());
            finalFilePath = existingFile.getUrl();
            existingPdfUrl = existingFile.getPdfUrl();
            initialStatus = existingFile.getStatus();
        } else {
            // 缓存到服务器本地
            String uuId = java.util.UUID.randomUUID().toString();
            localCachePath = uploadCache(file, extension, uuId);
            // 创建 simulated OSS 上传地址
            finalFilePath = "/tmp/wisepen/upload/oss/" + uuId + "." + extension;
            initialStatus = isOffice ? FileConstants.UPLOAD_STATUS_PROCESSING : FileConstants.UPLOAD_STATUS_AVAILABLE;
        }

        // 2. 数据库逻辑
        Long userId = StpUtil.getLoginIdAsLong();

        FileInfo fileInfo = FileInfo.builder()
                .filename(originalFilename)
                .md5(uploadRequest.getMd5())
                .type(extension)
                .size(file.getSize())
                .url(finalFilePath)
                .pdfUrl(existingPdfUrl)
                .createBy(userId)
                .status(initialStatus)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        fileMapper.insert(fileInfo);

        // 3. Redis 队列逻辑 (事务提交后执行)
        final String effectiveCachePath = localCachePath;
        final String effectiveFinalPath = finalFilePath;
        final boolean isNewFile = (existingFile == null);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 3.1 推送上传任务 (仅新文件)
                if (isNewFile) {
                    FileUploadTaskDTO uploadTask = FileUploadTaskDTO.builder()
                            .fileId(fileInfo.getId())
                            .originalFilename(originalFilename)
                            .tempFilePath(effectiveCachePath)
                            .targetPath(effectiveFinalPath)
                            .md5(uploadRequest.getMd5())
                            .build();
                    stringRedisTemplate.opsForList().leftPush(FileConstants.UPLOAD_QUEUE_KEY, JSON.toJSONString(uploadTask));
                    log.info("Pushed upload task to Redis for fileId: {}", fileInfo.getId());

                    // 3.2 推送转换任务 (仅新文件中的 Office 文档)
                    if (isOffice) {
                        FileConvertTaskDTO convertTask = FileConvertTaskDTO.builder()
                                .fileId(fileInfo.getId())
                                .originalFilename(originalFilename)
                                .extension(extension)
                                .tempFilePath(effectiveCachePath)
                                .originalSize(file.getSize())
                                .md5(uploadRequest.getMd5())
                                .build();
                        stringRedisTemplate.opsForList().leftPush(FileConstants.CONVERT_QUEUE_KEY, JSON.toJSONString(convertTask));
                        log.info("Pushed conversion task to Redis for fileId: {}, using path: {}", 
                                fileInfo.getId(), effectiveCachePath);
                    }
                }
            }
        });
    }

    private boolean isOfficeDocument(String extension) {
        if (extension == null) {
            return false;
        }
        return FileConstants.OFFICE_EXTENSIONS.contains(extension.toLowerCase());
    }

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
}
