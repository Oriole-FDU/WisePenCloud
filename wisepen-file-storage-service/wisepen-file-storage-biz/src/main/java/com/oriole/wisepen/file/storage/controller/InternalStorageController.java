package com.oriole.wisepen.file.storage.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.storage.api.domain.dto.CopyReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.service.IStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;

import java.util.List;

@Tag(name = "内部 - 文件存储", description = "供业务微服务初始化上传、获取下载地址与管理物理文件")
@RestController
@RequestMapping("/internal/storage/")
@RequiredArgsConstructor
public class InternalStorageController {

    private final IStorageService storageService;

    @Operation(summary = "内部上传文档图片", description = """
            - 用途：同步保存文档转换生成的正文图片。
            - 请求：file 为图片；bizTag 为 documentId/assets/images；fileName 为固定图片名；configId 可选。
            - 约束：仅内部服务调用；场景固定为 PRIVATE_DOC_ASSET；文件受现有小文件大小限制；已删除记录不能恢复。
            - 处理：上传到文档版本目录并登记 AVAILABLE，同名图片正常覆盖，不发送上传回调事件。
            - 失败：路径无效 -> CommonError.REQUEST_PARAM_INVALID；文件过大 -> FileStorageError.FILE_SIZE_ABOVE_UPPER_BOUND；已删除 -> FileStorageError.FILE_RECORD_NOT_FOUND。
            - 响应：返回可立即读取的存储记录及 objectKey。
            """)
    @Log(title = "上传文档图片", businessType = BusinessType.INSERT, isSaveRequestData = false, isSaveResponseData = false)
    @PostMapping(value = "/uploadDocumentAsset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<StorageRecordDTO> uploadDocumentAsset(@RequestPart("file") MultipartFile file,
                                                    @RequestParam("bizTag") String bizTag,
                                                    @RequestParam("fileName") String fileName,
                                                    @RequestParam(value = "configId", required = false) Long configId) {
        return R.ok(storageService.uploadDocumentAsset(file, bizTag, fileName, configId));
    }

    @Operation(summary = "内部查询文档图片文件标识", description = """
            - 用途：按文档版本目录收集正文图片的 objectKey，供调用方发布文件删除消息。
            - 请求：请求体为完整 private/docs/{documentId}/ 前缀列表；afterObjectKey 可选，续查时传上一页最后一个 key；size 为每页数量。
            - 约束：仅内部服务调用；不接受更宽泛的目录或通配符；size 必须在 1 至 500 之间。
            - 处理：只查询 PRIVATE_DOC_ASSET 场景中未软删除的存储记录，按 objectKey 升序返回游标之后的结果；不访问 OSS 或修改记录。
            - 失败：目录或每页数量无效 -> CommonError.REQUEST_PARAM_INVALID。
            - 响应：至多 size 个正文图片的 key；空前缀列表或无后续匹配记录时返回空列表。
            """)
    @PostMapping("/listDocumentAssetObjectKeysByPrefixes")
    public R<List<String>> listDocumentAssetObjectKeysByPrefixes(@RequestBody List<String> prefixes,
            @RequestParam(value = "afterObjectKey", required = false) String afterObjectKey,
            @RequestParam("size") int size) {
        return R.ok(storageService.listDocumentAssetObjectKeysByPrefixes(prefixes, afterObjectKey, size));
    }

    /**
     * 初始化上传（颁发 PUT URL 或触发秒传）
     */
    @Operation(
            summary = "内部初始化文件上传",
            description = """
                    - 用途：供业务服务为大文件或私有文件申请对象存储直传凭证。
                    - 请求：md5 用于同配置下秒传判定；extension 为文件后缀；scene 决定存储前缀；bizTag 决定业务目录；configId 可指定存储配置；expectedSize 为预期大小。
                    - 约束：调用方必须通过内部服务调用边界；scene 和 extension 必须由上游业务确认合法。
                    - 处理：优先按 md5 和配置查询可复用文件；命中时在对象存储内复制文件、创建新记录并发布上传完成事件；未命中时创建 UPLOADING 记录并返回 PUT 上传凭证。
                    - 失败：存储配置不支持 -> FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER；对象存储生成回调策略失败 -> FileStorageError.STORAGE_PROVIDER_GENERATE_CALLBACK_POLICY_FAILED；秒传复制文件失败 -> FileStorageError.STORAGE_PROVIDER_COPY_FILE_FAILED。
                    - 响应：返回 objectKey、域名、上传凭证和是否秒传。
                    """
    )
    @PostMapping("/initUpload")
    public R<UploadInitRespDTO> initUpload(@Validated @RequestBody UploadInitReqDTO req) {
        return R.ok(storageService.initUpload(req));
    }

    @PostMapping("/copy")
    public R<StorageRecordDTO> copyFile(@Validated @RequestBody CopyReqDTO req) {
        return R.ok(storageService.copyFile(req));
    }

    /**
     * 获取单文件的防盗链下载 URL
     */
    @Operation(
            summary = "内部获取文件下载地址",
            description = """
                    - 用途：供业务服务为已上传文件生成限时下载地址。
                    - 请求：objectKey 指定目标文件；duration 指定下载地址有效时长，默认 900 秒；contentDisposition 可选，用于覆盖对象存储下载响应的 Content-Disposition。
                    - 约束：调用方必须通过内部服务调用边界；目标文件记录必须存在且未删除。
                    - 处理：查询存储记录；若仍是 UPLOADING 会尝试补偿检查上传状态；确认可用后按对应存储配置生成防盗链下载 URL；不改变业务资源状态。
                    - 失败：文件记录不存在或仍未上传完成 -> FileStorageError.FILE_RECORD_NOT_FOUND；存储配置不支持 -> FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER；存储服务生成下载地址失败 -> FileStorageError.STORAGE_PROVIDER_GET_FILE_DOWNLOAD_URL_FAILED。
                    - 响应：返回限时下载 URL。
                    """
    )
    @GetMapping("/getDownloadUrl")
    public R<String> getDownloadUrl(
            @RequestParam("objectKey") String objectKey,
            @RequestParam(value = "duration", defaultValue = "900") Long duration,
            @RequestParam(value = "contentDisposition", required = false) String contentDisposition) {
        return R.ok(storageService.getDownloadUrl(objectKey, duration, contentDisposition));
    }

    /**
     * 颁发 STS 临时凭证（用于前端批量访问特定目录下的图片）
     */
    @Operation(
            summary = "内部获取临时访问凭证",
            description = """
                    - 用途：供业务服务为前端批量访问指定目录下的文件申请临时凭证。
                    - 请求：scene 决定授权目录前缀；bizTag 可继续限定业务目录；configId 可指定存储配置；durationSeconds 指定凭证有效时长。
                    - 约束：调用方必须通过内部服务调用边界；scene 必须是合法存储场景。
                    - 处理：拼接 scene 与 bizTag 得到目录通配前缀，并从目标存储配置申请只覆盖该前缀的 STS 凭证；不创建或删除文件记录。
                    - 失败：存储配置不支持 -> FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER；云厂商临时凭证申请失败 -> FileStorageError.STORAGE_PROVIDER_GENERATE_STS_TOKEN_FAILED。
                    - 响应：返回临时访问凭证及其有效期信息。
                    """
    )
    @GetMapping("/getStsToken")
    public R<StsTokenDTO> getStsToken(
            @RequestParam("scene") StorageSceneEnum scene,
            @RequestParam(value = "bizTag", required = false) String bizTag,
            @RequestParam(value = "configId", required = false) Long configId,
            @RequestParam(value = "durationSeconds", defaultValue = "3600") Long durationSeconds) {
        return R.ok(storageService.getStsToken(scene, bizTag, configId, durationSeconds));
    }

    /**
     * 物理删除文件
     */
    @Operation(
            summary = "内部删除文件记录",
            description = """
                    - 用途：供业务服务或异步消费者标记一批对象存储文件为已删除。
                    - 请求：请求体为 objectKey 列表。
                    - 约束：调用方必须通过内部服务调用边界；至少一个目标文件记录应存在且未删除。
                    - 处理：批量将匹配的存储记录状态更新为 DELETED；当前实现只更新记录状态，不直接调用云厂商物理删除。
                    - 失败：数据库更新发生未处理异常 -> CommonError.INTERNAL_ERROR。
                    - 响应：成功时返回空结果。
                    """
    )
    @DeleteMapping("/deleteFiles")
    public R<Void> deleteFiles(@RequestBody List<String> objectKeys) {
        storageService.deleteFiles(objectKeys);
        return R.ok();
    }

    /**
     * 获取文件物理记录明细 (主动查单/补偿)
     */
    @Operation(
            summary = "内部获取文件记录",
            description = """
                    - 用途：供业务服务主动查询上传文件是否已经完成，用于上传状态补偿。
                    - 请求：objectKey 指定目标文件。
                    - 约束：调用方必须通过内部服务调用边界。
                    - 处理：查询文件记录；不存在或已删除时返回空数据；上传中记录会尝试补偿检查云端状态；可用记录会补充存储域名。
                    - 失败：存储配置不支持 -> FileStorageError.CANNOT_SUPPORT_STORAGE_PROVIDER；云端状态补偿读取失败 -> FileStorageError.STORAGE_PROVIDER_READ_FILE_FAILED。
                    - 响应：返回文件记录；未找到或已删除时返回空数据。
                    """
    )
    @GetMapping("/getFileRecord")
    public R<StorageRecordDTO> getFileRecord(@RequestParam("objectKey") String objectKey) {
        return R.ok(storageService.getFileRecord(objectKey));
    }
}
