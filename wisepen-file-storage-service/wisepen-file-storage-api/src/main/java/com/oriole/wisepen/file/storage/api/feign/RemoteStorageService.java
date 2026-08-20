package com.oriole.wisepen.file.storage.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 获取单文件的防盗链下载 URL
     */
    @GetMapping("/getDownloadUrl")
    R<String> getDownloadUrl(@RequestParam("objectKey") String objectKey,
                             @RequestParam(value = "duration", required = false) Long duration,
                             @RequestParam(value = "contentDisposition", required = false) String contentDisposition);

    /**
     * 颁发 STS 临时凭证（用于前端批量访问特定目录下的图片）
     */
    @GetMapping("/getStsToken")
    R<StsTokenDTO> getStsToken(@RequestParam("scene") StorageSceneEnum scene,
                               @RequestParam(value = "bizTag", required = false) String bizTag,
                               @RequestParam(value = "configId", required = false) Long configId,
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
