package com.oriole.wisepen.file.storage.service;

import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.domain.entity.StorageRecordEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IStorageService {

    /**
     * 初始化文件上传（包含 MD5 秒传判定与直传凭证签发）
     */
    UploadInitRespDTO initUpload(UploadInitReqDTO req);

    /**
     * 复制已有文件对象并创建独立存储记录
     */
    StorageRecordDTO copyFile(CopyReqDTO req);

    /**
     * 小文件代理上传
     * @param scene           场景隔离标识
     * @param bizTag         业务路径隔离标识
     */
    StorageRecordDTO uploadSmallFileProxy(MultipartFile file, StorageSceneEnum scene, String bizTag);

    /**
     * 获取带自定义响应头的单文件的私有下载链接
     *
     * @param objectKey                  文件对象相对路径
     * @param durationSeconds            链接有效时长（秒）
     * @param contentDisposition         OSS 下载响应的 Content-Disposition
     */
    String getDownloadUrl(String objectKey, Long durationSeconds, String contentDisposition);

    /**
     * 颁发 STS 临时凭证（支持前端批量加载受保护目录）
     * @param scene           场景隔离标识
     * @param bizTag         业务路径隔离标识
     * @param configId        目标存储源ID（如业务方不指定，则降级使用 Primary 源）
     * @param durationSeconds 凭证有效时长
     */
    StsTokenDTO getStsToken(StorageSceneEnum scene, String bizTag, Long configId, Long durationSeconds);

    /**
     * 软删除文件
     * @param objectKeys       文件对象相对路径
     */
    void deleteFiles(List<String> objectKeys);

    /**
     * 处理云厂商的上传成功回调
     */
    void handleUploadCallback(HttpServletRequest request, String rawBody);

    StorageRecordDTO getFileRecord(String objectKey);

    StorageRecordDTO compensateStatus(StorageRecordEntity record);
}
