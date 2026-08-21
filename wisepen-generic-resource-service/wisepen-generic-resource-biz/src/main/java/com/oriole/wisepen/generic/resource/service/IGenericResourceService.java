package com.oriole.wisepen.generic.resource.service;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.generic.resource.api.domain.dto.req.GenericResourceUploadInitRequest;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceInfoResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceDownloadResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadInitResponse;
import com.oriole.wisepen.generic.resource.api.domain.dto.res.GenericResourceUploadStatusResponse;

import java.util.List;
import java.util.Map;

public interface IGenericResourceService {

    GenericResourceUploadInitResponse initUploadGenericResource(GenericResourceUploadInitRequest request, Long uploaderId, Map<Long, GroupRoleType> uploaderGroupRoles);

    GenericResourceUploadStatusResponse syncGenericResourceUploadStatus(String genericResourceId, Long operatorUserId);

    GenericResourceInfoResponse getGenericResourceInfo(String resourceId);

    GenericResourceDownloadResponse getDownloadUrl(String resourceId, Long durationSeconds);

    void handleFileUploaded(FileUploadedMessage message);

    void deleteGenericResources(List<String> resourceIds);
}
