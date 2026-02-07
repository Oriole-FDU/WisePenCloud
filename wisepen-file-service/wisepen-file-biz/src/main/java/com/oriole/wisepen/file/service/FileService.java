package com.oriole.wisepen.file.service;

import com.oriole.wisepen.file.api.domain.dto.UploadRequest;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface FileService {
    void upload(MultipartFile file, UploadRequest uploadRequest) throws IOException;
}
