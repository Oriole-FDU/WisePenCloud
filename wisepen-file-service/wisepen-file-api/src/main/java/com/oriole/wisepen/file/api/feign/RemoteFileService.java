package com.oriole.wisepen.file.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.file.api.domain.dto.FileInfoVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "remoteFileService", value = "wisepen-file-service")
public interface RemoteFileService {

    /**
     * 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    @GetMapping("/remote/file/info/{fileId}")
    R<FileInfoVO> getFileInfo(@PathVariable("fileId") Long fileId);
}
