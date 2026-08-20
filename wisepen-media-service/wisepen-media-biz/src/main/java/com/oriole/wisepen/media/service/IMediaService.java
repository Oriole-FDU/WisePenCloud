package com.oriole.wisepen.media.service;

import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.dto.req.MediaUploadInitRequest;
import com.oriole.wisepen.media.api.domain.dto.res.MediaInfoResponse;
import com.oriole.wisepen.media.api.domain.dto.res.MediaUploadInitResponse;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;

import java.util.List;
import java.util.Map;

public interface IMediaService {

    MediaUploadInitResponse initUploadMedia(MediaUploadInitRequest request, Long uploaderId,
                                            Map<Long, GroupRoleType> uploaderGroupRoles);

    List<MediaInfoResponse> listPendingMedia(Long uploaderId);

    MediaStatus refreshMediaStatus(String mediaId);

    void retryMediaProcess(String mediaId);

    void assertMediaUploader(String mediaId, Long uploaderId);

    void handleFileUploaded(FileUploadedMessage message);

    MediaInfoResponse getMediaInfo(String resourceId, ResourceItemResponse resourceInfo);

    String getOriginalDownloadUrl(String resourceId);

    void deleteMediaByResourceIds(List<String> resourceIds);
}
