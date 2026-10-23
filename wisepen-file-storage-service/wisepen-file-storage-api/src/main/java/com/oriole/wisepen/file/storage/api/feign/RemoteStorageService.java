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
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

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

    /** 同步上传小文件并生成固定文件名的存储记录，成功后即可读取，无上传回调。 */
    @PostMapping(value = "/uploadSmallFileWithName", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    R<StorageRecordDTO> uploadSmallFileWithName(@RequestPart("file") MultipartFile file,
                                        @RequestParam("scene") StorageSceneEnum scene,
                                        @RequestParam("bizTag") String bizTag,
                                        @RequestParam("fileName") String fileName,
                                        @RequestParam(value = "configId", required = false) Long configId);

    /** 按前缀游标分页查询未删除的存储记录 objectKey。 */
    @PostMapping("/listObjectKeysByPrefixes")
    R<List<String>> listObjectKeysByPrefixes(@RequestBody List<String> prefixes,
            @RequestParam(value = "afterObjectKey", required = false) String afterObjectKey,
            @RequestParam("size") int size);

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
