package com.oriole.wisepen.file.storage.strategy;
import com.oriole.wisepen.file.storage.api.domain.base.StorageRecordBase;
import com.oriole.wisepen.file.storage.api.domain.base.UploadUrlBase;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

public interface StorageProvider {

    /**
     * 获取当前实例对应的配置 ID
     */
    Long getConfigId();

    /**
     * 获取当前实例绑定的对外访问域名
     */
    String getDomain();

    /**
     * 生成客户端直传凭证（预签名 PUT URL 或表单凭证）
     *
     * @param objectKey       目标文件在存储中的全路径
     * @param durationSeconds 凭证有效时长（秒）
     * @return 上传凭证URL
     */
    UploadUrlBase generateUploadTicket(String objectKey, long durationSeconds, String apiDomain, Boolean isNeedCallback);

    /**
     * 生成带自定义响应头的私有文件下载链接。
     *
     * @param objectKey                  目标文件全路径
     * @param durationSeconds            下载链接有效时长（秒）
     * @param contentDisposition 下载响应的 Content-Disposition，可为空
     * @return 下载直链
     */
    String generateDownloadUrl(String objectKey, long durationSeconds, String contentDisposition);

    /**
     * 生成 STS Token
     *
     * @param pathPrefix       拟授权目标文件所在路径
     * @param durationSeconds  STS有效时长（秒）
     * @return 下载直链
     */
    StsTokenDTO getStsToken(String pathPrefix, long durationSeconds);

    /**
     * 拷贝物理文件
     *
     * @param sourceKey 源文件全路径
     * @param targetKey 目标文件全路径
     */
    void copyObject(String sourceKey, String targetKey);

    /**
     * 删除物理文件
     *
     * @param objectKey 目标文件全路径
     */
    void deleteObject(String objectKey);

    /**
     * 代理上传小文件 (图床模式)
     *
     * @param file      字节流
     * @param objectKey 目标路径
     */
    void uploadSmallFile(MultipartFile file, String objectKey);

    /**
     * 校验云厂商异步回调请求的真实性（防伪造回调）
     *
     * @param request HTTP 请求
     * @return 校验通过返回 true
     */
    boolean verifyCallbackSignature(HttpServletRequest request, String rawBody);

    /**
     * 主动查询云端物理文件的元数据 (大小、MD5等)
     * @param objectKey 相对路径
     * @return 如果文件存在则返回包含 size 和 md5 的 DTO；如果不存在返回 null
     */
    StorageRecordBase getObjectMetadata(String objectKey);

    /** 销毁实例，释放连接池资源 */
    void destroy();
}
