package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementAttachmentUploadInitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementPublishRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementAttachmentUploadInitResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementListItemResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementReadMemberResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementReadStatsResponse;

public interface IGroupAnnouncementService {

    GroupAnnouncementAttachmentUploadInitResponse initAttachmentUpload(
            GroupAnnouncementAttachmentUploadInitRequest req, Long operatorUserId);

    Long publishAnnouncement(GroupAnnouncementPublishRequest req, Long publisherId);

    void updateAnnouncement(GroupAnnouncementUpdateRequest req, Long operatorUserId);

    void deleteAnnouncement(Long groupId, Long announcementId, Long operatorUserId);

    PageR<GroupAnnouncementListItemResponse> listAnnouncements(Long groupId, Long userId, int page, int size);

    GroupAnnouncementDetailResponse getAnnouncementDetail(Long groupId, Long announcementId, Long userId);

    String getAttachmentDownloadUrl(Long groupId, Long announcementId, Long attachmentId);

    GroupAnnouncementReadStatsResponse getReadStats(Long groupId, Long announcementId, Long operatorUserId);

    PageR<GroupAnnouncementReadMemberResponse> listReadMembers(
            Long groupId, Long announcementId, Long operatorUserId, boolean read, int page, int size);
}
