package com.oriole.wisepen.file.storage.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.DownloadUrlsReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 内部存储服务 Feign 客户端
 * 提供给其他业务微服务调用
 */
@FeignClient(contextId = "remoteStorageService", value = "wisepen-file-storage-service", path = "/internal/storage")
public interface RemoteStorageService {

    /**
     * 初始化上传（颁发 PUT URL 或触发秒传）
     */
    @PostMapping("/initUpload")
    R<UploadInitRespDTO> initUpload(@RequestBody UploadInitReqDTO req);

    /**
     * 复制已有文件对象并生成独立存储记录
     */
    @PostMapping("/copy")
    R<StorageRecordDTO> copyFile(@RequestBody CopyReqDTO req);

    /**
     * 由内部业务服务代理上传小文件内容
     */
    @PostMapping(value = "/uploadSmallFile", consumes = "application/octet-stream")
    R<StorageRecordDTO> uploadSmallFile(@RequestBody byte[] content,
                                        @RequestParam("extension") String extension,
                                        @RequestParam("scene") StorageSceneEnum scene,
                                        @RequestParam("bizTag") String bizTag);

    /**
     * 由内部业务服务读取小文件内容
     */
    @GetMapping("/getSmallFileContent")
    R<byte[]> getSmallFileContent(@RequestParam("objectKey") String objectKey);

    /**
     * 获取单文件的防盗链下载 URL
     */
    @GetMapping("/getDownloadUrl")
    R<String> getDownloadUrl(@RequestParam("objectKey") String objectKey,
                             @RequestParam(value = "duration", required = false) Long duration,
                             @RequestParam(value = "contentDisposition", required = false) String contentDisposition);

    /** 批量获取文件下载地址；任一文件不存在时整批失败。 */
    @PostMapping("/getDownloadUrls")
    R<Map<String, String>> getDownloadUrls(@RequestBody DownloadUrlsReqDTO req);

    /**
     * 颁发 STS 临时凭证（用于前端批量访问特定目录下的图片）
     */
    @GetMapping("/getStsToken")
    R<StsTokenDTO> getStsToken(@RequestParam("scene") StorageSceneEnum scene,
                               @RequestParam(value = "bizTag", required = false) String bizTag,
                               @RequestParam(value = "providerCode", required = false) String providerCode,
                               @RequestParam(value = "durationSeconds", required = false) Long durationSeconds);

    /**
     * 物理删除文件
     */
    @DeleteMapping("/deleteFiles")
    R<Void> deleteFiles(@RequestBody List<String> objectKeys);

    /**
     * 获取文件物理记录明细 (主动查单/补偿)
     */
    @GetMapping("/getFileRecord")
    R<StorageRecordDTO> getFileRecord(@RequestParam("objectKey") String objectKey);
}
