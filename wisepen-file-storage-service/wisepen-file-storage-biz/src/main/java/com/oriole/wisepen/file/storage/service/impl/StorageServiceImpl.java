package com.oriole.wisepen.file.storage.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.base.StorageRecordBase;
import com.oriole.wisepen.file.storage.api.domain.base.UploadUrlBase;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import com.oriole.wisepen.file.storage.config.StorageProperties;
import com.oriole.wisepen.file.storage.domain.entity.StorageRecordEntity;
import com.oriole.wisepen.file.storage.exception.FileStorageError;
import com.oriole.wisepen.file.storage.mapper.StorageRecordMapper;
import com.oriole.wisepen.file.storage.mq.KafkaStorageEventPublisher;
import com.oriole.wisepen.file.storage.service.IStorageService;
import com.oriole.wisepen.file.storage.strategy.StorageManager;
import com.oriole.wisepen.file.storage.strategy.StorageProvider;
import jakarta.servlet.http.HttpServletRequest;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements IStorageService {

    private final StorageManager storageManager;
    private final StorageRecordMapper storageRecordMapper;
    private final KafkaStorageEventPublisher eventPublisher;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @WithSpan("storage.upload.initialize")
    public UploadInitRespDTO initUpload(UploadInitReqDTO req) {
        StorageProvider provider = storageManager.getProvider(req.getProviderCode());

        // 查询库中是否存在同 MD5 且同配置区的文件 (秒传判定)
        StorageRecordEntity existRecord = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getMd5, req.getMd5())
                        .eq(StorageRecordEntity::getProviderCode, provider.getProviderCode())
                        .last("LIMIT 1")
        );

        String newObjectKey = buildObjectKey(req.getScene().getPrefix(), req.getBizTag(), req.getExtension());
        String domain = provider.getDomain();

        if (existRecord != null) {
            log.info("storage flash upload started. md5={} sourceObjectKey={} targetObjectKey={}",
                    req.getMd5(), existRecord.getObjectKey(), newObjectKey);
            // 触发 OSS 内部物理克隆
            try {
                provider.copyObject(existRecord.getObjectKey(), newObjectKey);
            }  catch (Exception e) {
                // 秒传异常，获取直传 PUT URL
                return this.getPutUrl(req.getScene(), newObjectKey, provider, req.getIsNeedCallback());
            }

            StorageRecordEntity newRecord = BeanUtil.copyProperties(existRecord, StorageRecordEntity.class,
                    "fileId", "createTime", "objectKey");
            newRecord.setStatus(StorageStatusEnum.AVAILABLE);
            newRecord.setObjectKey(newObjectKey);
            storageRecordMapper.insert(newRecord);

            FileUploadedMessage fileUploadedMessage = BeanUtil.copyProperties(newRecord, FileUploadedMessage.class);
            fileUploadedMessage.setDomain(provider.getDomain());
            fileUploadedMessage.setFlashUploaded(true);
            // 发送 Kafka 事件
            eventPublisher.publishFileUploadedEvent(fileUploadedMessage);
            log.info("storage record created. objectKey={} status={} flashUploaded=true", newObjectKey, newRecord.getStatus());

            return UploadInitRespDTO.builder()
                    .flashUploaded(true)
                    .domain(domain)
                    .objectKey(newObjectKey)
                    .build();
        }

        // 没命中秒传，获取直传 PUT URL
        return this.getPutUrl(req.getScene(), newObjectKey, provider, req.getIsNeedCallback());
    }

    private StorageRecordEntity getStorageRecordWithCompensateStatus(String ObjectKey){
        StorageRecordEntity entity = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getObjectKey,ObjectKey)
                        .ne(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
                        .last("LIMIT 1")
        );
        if (entity == null) throw new ServiceException(FileStorageError.FILE_RECORD_NOT_FOUND);

        // 检查补偿
        if (StorageStatusEnum.UPLOADING.equals(entity.getStatus())) {
            StorageRecordDTO dto = this.compensateStatus(entity);
            if (dto == null) throw new ServiceException(FileStorageError.FILE_RECORD_NOT_FOUND);
            entity = storageRecordMapper.selectById(dto.getFileId());
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageRecordDTO copyFile(CopyReqDTO req) {
        StorageRecordEntity sourceRecord = getStorageRecordWithCompensateStatus(req.getSourceObjectKey());

        // 同源复制
        StorageProvider provider = storageManager.getProvider(sourceRecord.getProviderCode());

        // 根据请求的 Scene 和 BizTag 重新构建 ObjectKey
        String extension = FileUtil.extName(sourceRecord.getObjectKey()).toLowerCase();
        String targetObjectKey = buildObjectKey(req.getScene().getPrefix(), req.getBizTag(), extension);

        try {
            provider.copyObject(sourceRecord.getObjectKey(), targetObjectKey);

            StorageRecordEntity targetRecord = StorageRecordEntity.builder()
                    .objectKey(targetObjectKey).scene(req.getScene())
                    .md5(sourceRecord.getMd5()).size(sourceRecord.getSize()).providerCode(sourceRecord.getProviderCode())
                    .status(StorageStatusEnum.AVAILABLE)
                    .build();
            storageRecordMapper.insert(targetRecord);

            StorageRecordDTO dto = BeanUtil.copyProperties(targetRecord, StorageRecordDTO.class);
            dto.setDomain(provider.getDomain());
            return dto;
        } catch (Exception e) {
            try {
                // 失败时回滚
                provider.deleteObject(targetObjectKey);
            } catch (Exception cleanupEx) {
                log.warn("clean object after copy failure failed. objectKey={}", targetObjectKey, cleanupEx);
            }
            throw e;
        }
    }

    private UploadInitRespDTO getPutUrl(StorageSceneEnum scene, String newObjectKey, StorageProvider provider, Boolean isNeedCallback) {
        StorageRecordEntity newRecord = StorageRecordEntity.builder()
                .scene(scene).objectKey(newObjectKey).status(StorageStatusEnum.UPLOADING).providerCode(provider.getProviderCode())
                .build();
        storageRecordMapper.insert(newRecord);
        UploadUrlBase uploadUrl = provider.generateUploadTicket(newObjectKey, storageProperties.getUpload().getDefaultTicketDuration(), storageProperties.getEndpoint().getApiDomain(), isNeedCallback);

        log.info("storage upload ticket created. objectKey={} status={}", newObjectKey, StorageStatusEnum.UPLOADING);

        UploadInitRespDTO dto = UploadInitRespDTO.builder()
                .flashUploaded(false).objectKey(newObjectKey).domain(provider.getDomain())
                .build();
        BeanUtil.copyProperties(uploadUrl, dto, CopyOptions.create().ignoreNullValue());
        return dto;
    }

    @Override
    public StorageRecordDTO uploadSmallFileProxy(MultipartFile file, StorageSceneEnum scene, String bizTag) {
        try {
            return uploadSmallFile(file.getBytes(), FileUtil.extName(file.getOriginalFilename()), scene, bizTag);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(FileStorageError.STORAGE_PROVIDER_UPLOAD_FILE_FAILED);
        }
    }

    @Override
    public StorageRecordDTO uploadSmallFile(byte[] content, String extension, StorageSceneEnum scene, String bizTag) {
        if (content == null || content.length == 0 || content.length > storageProperties.getUpload().getMaxSmallFileSize()) {
            throw new ServiceException(FileStorageError.FILE_SIZE_ABOVE_UPPER_BOUND);
        }
        if (StrUtil.isBlank(extension) || !extension.matches("[A-Za-z0-9]+")) {
            throw new ServiceException(FileStorageError.CANNOT_SUPPORT_FILE_TYPE);
        }

        String objectKey = buildObjectKey(scene.getPrefix(), bizTag, extension.toLowerCase());
        StorageProvider provider = storageManager.getPrimaryProvider();
        provider.uploadSmallFile(content, objectKey);

        StorageRecordEntity newRecord = StorageRecordEntity.builder()
                .scene(scene)
                .objectKey(objectKey)
                .md5(DigestUtil.md5Hex(content))
                .size((long) content.length)
                .providerCode(provider.getProviderCode())
                .status(StorageStatusEnum.AVAILABLE)
                .build();
        storageRecordMapper.insert(newRecord);

        log.info("small file uploaded. objectKey={} size={} status={}",
                objectKey, content.length, StorageStatusEnum.AVAILABLE);

        StorageRecordDTO dto = BeanUtil.copyProperties(newRecord, StorageRecordDTO.class);
        dto.setDomain(provider.getDomain());
        return dto;
    }

    @Override
    public byte[] getSmallFileContent(String objectKey) {
        StorageRecordEntity record = getStorageRecordWithCompensateStatus(objectKey);
        if (record.getSize() != null && record.getSize() > storageProperties.getUpload().getMaxSmallFileSize()) {
            throw new ServiceException(FileStorageError.FILE_SIZE_ABOVE_UPPER_BOUND);
        }
        StorageProvider provider = storageManager.getProvider(record.getProviderCode());
        return provider.getSmallFileContent(objectKey, storageProperties.getUpload().getMaxSmallFileSize());
    }

    @Override
    public String getDownloadUrl(String objectKey, Long durationSeconds, String contentDisposition) {
        StorageRecordEntity record = getStorageRecordWithCompensateStatus(objectKey);

        StorageProvider provider = storageManager.getProvider(record.getProviderCode());
        return provider.generateDownloadUrl(objectKey, durationSeconds, contentDisposition);
    }

    @Override
    public void deleteFiles(List<String> objectKeys) {
        int updateCount = storageRecordMapper.update(null,
                Wrappers.<StorageRecordEntity>lambdaUpdate()
                        .set(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
                        .in(StorageRecordEntity::getObjectKey, objectKeys)
                        .ne(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
        );

        if (updateCount == 0) {
            throw new ServiceException(FileStorageError.FILE_RECORD_NOT_FOUND);
        }

        log.info("storage records deleted. mode=soft count={} objectKeys={}", updateCount, summarizeIds(objectKeys));
    }

    @Override
    public StsTokenDTO getStsToken(StorageSceneEnum scene, String bizTag, String providerCode, Long durationSeconds) {
        String cleanBizTag = StrUtil.isNotBlank(bizTag)
                ? bizTag.replaceAll("^/+", "").replaceAll("/+$", "")
                : "";
        String pathPrefix = scene.getPrefix();
        if (StrUtil.isNotBlank(cleanBizTag)) {
            pathPrefix += "/" + cleanBizTag;
        }
        pathPrefix += "/*"; // 加上通配符，授予该目录及子目录下的读取权限
        // 如果业务方不指定 providerCode，StorageManager 使用首要存储提供商
        StorageProvider provider = storageManager.getProvider(providerCode);
        return provider.getStsToken(pathPrefix, durationSeconds);
    }

    @Override
    public void handleUploadCallback(HttpServletRequest request, String rawBody) {
        // 解析参数
        Map<String, String> paramMap = HttpUtil.decodeParamMap(rawBody, CharsetUtil.CHARSET_UTF_8);
        String objectKey = paramMap.get("objectKey");
        String md5 = paramMap.get("md5");
        Long size = paramMap.containsKey("size") ? Long.parseLong(paramMap.get("size")) : null;

        StorageRecordEntity record = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getObjectKey, objectKey)
                        .last("LIMIT 1")
        );

        StorageProvider provider = storageManager.getProvider(record.getProviderCode());

        if (!provider.verifyCallbackSignature(request, rawBody)) {
            throw new ServiceException(FileStorageError.STORAGE_PROVIDER_CALLBACK_SIGNATURE_INVALID);
        }

        if (StorageStatusEnum.AVAILABLE.equals(record.getStatus())) {
            log.debug("storage callback skipped. objectKey={} status={}", objectKey, record.getStatus());
            return;
        }

        record.setMd5(md5);
        record.setSize(size);
        record.setStatus(StorageStatusEnum.AVAILABLE);
        storageRecordMapper.updateById(record);

        FileUploadedMessage fileUploadedMessage = BeanUtil.copyProperties(record, FileUploadedMessage.class);
        fileUploadedMessage.setDomain(provider.getDomain());
        fileUploadedMessage.setFlashUploaded(false); // 非秒传

        eventPublisher.publishFileUploadedEvent(fileUploadedMessage);

        log.info("storage callback persisted. objectKey={} domain={} status={}", objectKey, provider.getDomain(), StorageStatusEnum.AVAILABLE);
    }

    private String buildObjectKey(String scenePrefix, String bizTag, String extension) {
        String cleanBizTag = StrUtil.isNotBlank(bizTag)
                ? bizTag.replaceAll("^/+", "").replaceAll("/+$", "")
                : DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        String fileName = IdUtil.fastSimpleUUID() + "." + extension;

        // 场景后缀 + 时间戳 / 业务Tag + 文件名
        // 例如 public/images/user/2026/1/1/XXXX.png private/images/note/XXXX/XXXX.png
        return Stream.of(scenePrefix, cleanBizTag, fileName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("/"));
    }

    @Override
    public StorageRecordDTO getFileRecord(String objectKey) {

        StorageRecordEntity record = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getObjectKey, objectKey)
                        .last("LIMIT 1")
        );

        if (record == null || StorageStatusEnum.DELETED.equals(record.getStatus())) {
            return null;
        }

        StorageProvider provider = storageManager.getProvider(record.getProviderCode());
        if (StorageStatusEnum.AVAILABLE.equals(record.getStatus())) {
            StorageRecordDTO dto = BeanUtil.copyProperties(record, StorageRecordDTO.class);
            dto.setDomain(provider.getDomain());
            return dto;
        }
        return this.compensateStatus(record);
    }

    @Override
    public StorageRecordDTO compensateStatus(StorageRecordEntity record) {
        StorageProvider provider = storageManager.getProvider(record.getProviderCode());
        log.warn("storage record recover started. objectKey={} domain={}",
                record.getObjectKey(), provider.getDomain());

        StorageRecordBase cloudRecord = provider.getObjectMetadata(record.getObjectKey());
        if (cloudRecord != null) {
            // 云端找到说明回调丢失，进行数据补偿
            BeanUtil.copyProperties(cloudRecord, record);
            record.setStatus(StorageStatusEnum.AVAILABLE);
            storageRecordMapper.updateById(record);

            StorageRecordDTO dto = BeanUtil.copyProperties(record, StorageRecordDTO.class);
            dto.setDomain(provider.getDomain());

            // 补发 Kafka 消息
            FileUploadedMessage fileUploadedMessage = BeanUtil.copyProperties(record, FileUploadedMessage.class);
            fileUploadedMessage.setDomain(provider.getDomain());
            fileUploadedMessage.setFlashUploaded(false);

            eventPublisher.publishFileUploadedEvent(fileUploadedMessage);

            log.info("storage record recovered. objectKey={} domain={} status={}", record.getObjectKey(), provider.getDomain(), StorageStatusEnum.AVAILABLE);
            return dto;
        }
        return null;
    }
}
