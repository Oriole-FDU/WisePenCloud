package com.oriole.wisepen.media.service;

import com.oriole.wisepen.media.api.domain.base.MediaBase;
import com.oriole.wisepen.media.api.domain.base.MediaStatus;
import com.oriole.wisepen.media.api.domain.dto.req.MediaUploadInitRequest;
import com.oriole.wisepen.media.api.domain.dto.res.MediaUploadInitResponse;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.media.domain.entity.MediaEntity;
import com.oriole.wisepen.media.domain.entity.MediaInfoEntity;
import com.oriole.wisepen.media.domain.entity.MediaMetaEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IMediaService {

    // 初始化上传
    MediaUploadInitResponse initUploadMedia(MediaUploadInitRequest request, Long uploaderId,
                                            Map<Long, GroupRoleType> uploaderGroupRoles);

    // 获取未就绪媒体列表
    List<MediaBase> listPendingMedia(Long uploaderId);

    // 获取媒体状态
    Optional<MediaStatus> getMediaStatus(String mediaId);

    // 刷新/获取媒体状态
    MediaStatus refreshMediaStatus(String mediaId);

    // 重试媒体处理
    void retryMediaProcess(String mediaId);

    // 取消媒体处理
    void cancelMediaProcess(String mediaId);

    // 断言媒体的所属权 (仅对未就绪文档有效)
    void assertMediaUploader(String mediaId, Long uploaderId);

    // 获取媒体信息
    MediaInfoEntity getMediaInfo(String resourceId);

    // 获取媒体
    MediaEntity getMedia(String resourceId);

    // 获取原始下载URL
    String getOriginalDownloadUrl(String resourceId);

    // 删除媒体
    void deleteMedia(List<String> resourceIds);

    // 更新媒体状态
    void updateStatus(String mediaId, MediaStatus status);

    // 归档媒体处理解析的结果
    void saveMediaProcessResult(String mediaId, String previewObjectKey, List<MediaEntity.MediaHlsRenditionObjectKeys> hlsObjectKeys, MediaMetaEntity meta);

    // 媒体就绪
    void finalizeToReady(String mediaId);
}