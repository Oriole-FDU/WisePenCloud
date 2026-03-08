package com.oriole.wisepen.file.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.domain.result.FileInfoResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;


/**
 * @author Ian.xiong
 */
@FeignClient(contextId = "remoteFileService", value = "wisepen-file-service")
public interface RemoteFileService {

    /**
     * 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    @GetMapping("/remote/file/info/{fileId}")
    R<FileInfoResult> getFileInfo(Long fileId);
}
