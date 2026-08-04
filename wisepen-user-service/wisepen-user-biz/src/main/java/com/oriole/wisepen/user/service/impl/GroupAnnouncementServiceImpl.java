package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.security.exception.PermissionError;
import com.oriole.wisepen.common.security.exception.PermissionException;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementAttachmentRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementAttachmentUploadInitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupAnnouncementUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementAttachmentResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementAttachmentUploadInitResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementListItemResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementReadMemberResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupAnnouncementReadStatsResponse;
import com.oriole.wisepen.user.domain.entity.GroupAnnouncementAttachmentEntity;
import com.oriole.wisepen.user.domain.entity.GroupAnnouncementEntity;
import com.oriole.wisepen.user.domain.entity.GroupAnnouncementReadEntity;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import com.oriole.wisepen.user.event.GroupAnnouncementNotificationEvent;
import com.oriole.wisepen.user.exception.UserError;
import com.oriole.wisepen.user.mapper.GroupAnnouncementAttachmentMapper;
import com.oriole.wisepen.user.mapper.GroupAnnouncementMapper;
import com.oriole.wisepen.user.mapper.GroupAnnouncementReadMapper;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.service.IGroupAnnouncementService;
import com.oriole.wisepen.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAnnouncementServiceImpl implements IGroupAnnouncementService {

    private final GroupAnnouncementMapper announcementMapper;
    private final GroupAnnouncementAttachmentMapper attachmentMapper;
    private final GroupAnnouncementReadMapper readMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final IUserService userService;
    private final RemoteStorageService remoteStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public GroupAnnouncementAttachmentUploadInitResponse initAttachmentUpload(
            GroupAnnouncementAttachmentUploadInitRequest req, Long operatorUserId) {
        if (groupMapper.selectById(req.getGroupId()) == null) {
            throw new ServiceException(UserError.GROUP_NOT_EXIST);
        }
        try {
            UploadInitRespDTO uploadResponse = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(req.getMd5())
                    .extension(req.getExtension())
                    .scene(StorageSceneEnum.PRIVATE_GROUP_ANNOUNCEMENT)
                    .bizTag(String.valueOf(req.getGroupId()))
                    .expectedSize(req.getExpectedSize())
                    .isNeedCallback(true)
                    .build()).getData();
            if (uploadResponse == null) {
                throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED);
            }
            return BeanUtil.copyProperties(uploadResponse, GroupAnnouncementAttachmentUploadInitResponse.class);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("group announcement attachment upload init failed. groupId={} userId={}",
                    req.getGroupId(), operatorUserId, e);
            throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAnnouncement(GroupAnnouncementCreateRequest req, Long publisherId) {
        if (groupMapper.selectById(req.getGroupId()) == null) {
            throw new ServiceException(UserError.GROUP_NOT_EXIST);
        }
        Map<String, StorageRecordDTO> storageRecords = validateAttachments(req.getGroupId(), req.getAttachments());
        LocalDateTime now = LocalDateTime.now();
        GroupAnnouncementEntity announcement = GroupAnnouncementEntity.builder()
                .announcementId(IdWorker.getId())
                .groupId(req.getGroupId())
                .publisherId(publisherId)
                .content(req.getContent())
                .createTime(now)
                .updateTime(now)
                .build();
        announcementMapper.insert(announcement);
        replaceAttachments(announcement.getAnnouncementId(), req.getAttachments(), storageRecords, now);

        List<Long> receiverUserIds = listCurrentMemberIds(req.getGroupId(), publisherId);
        eventPublisher.publishEvent(GroupAnnouncementNotificationEvent.builder()
                .announcementId(announcement.getAnnouncementId())
                .groupId(req.getGroupId())
                .title("小组公告")
                .content(req.getContent())
                .bizTraceId("group-announcement:%d:publish".formatted(announcement.getAnnouncementId()))
                .receiverUserIds(receiverUserIds)
                .build());
        log.info("group announcement created. groupId={} announcementId={} publisherId={} attachmentCount={}",
                req.getGroupId(), announcement.getAnnouncementId(), publisherId, storageRecords.size());
        return announcement.getAnnouncementId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(GroupAnnouncementUpdateRequest req, Long operatorUserId) {
        GroupAnnouncementEntity announcement = getAnnouncement(req.getGroupId(), req.getAnnouncementId());
        assertPublisher(announcement, operatorUserId);
        Map<String, StorageRecordDTO> storageRecords = validateAttachments(req.getGroupId(), req.getAttachments());
        LocalDateTime now = LocalDateTime.now();
        announcement.setContent(req.getContent());
        announcement.setUpdateTime(now);
        announcementMapper.updateById(announcement);
        replaceAttachments(announcement.getAnnouncementId(), req.getAttachments(), storageRecords, now);
        readMapper.delete(new LambdaQueryWrapper<GroupAnnouncementReadEntity>()
                .eq(GroupAnnouncementReadEntity::getAnnouncementId, announcement.getAnnouncementId()));

        List<Long> receiverUserIds = listCurrentMemberIds(req.getGroupId(), operatorUserId);
        eventPublisher.publishEvent(GroupAnnouncementNotificationEvent.builder()
                .announcementId(announcement.getAnnouncementId())
                .groupId(req.getGroupId())
                .title("小组公告已更新")
                .content(req.getContent())
                .bizTraceId("group-announcement:%d:update:%d".formatted(announcement.getAnnouncementId(), IdWorker.getId()))
                .receiverUserIds(receiverUserIds)
                .build());
        log.info("group announcement updated. groupId={} announcementId={} publisherId={} attachmentCount={}",
                req.getGroupId(), announcement.getAnnouncementId(), operatorUserId, storageRecords.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncement(Long groupId, Long announcementId, Long operatorUserId) {
        GroupAnnouncementEntity announcement = getAnnouncement(groupId, announcementId);
        assertPublisher(announcement, operatorUserId);
        announcementMapper.deleteById(announcementId);
        log.info("group announcement deleted. groupId={} announcementId={} publisherId={}",
                groupId, announcementId, operatorUserId);
    }

    @Override
    public PageR<GroupAnnouncementListItemResponse> listAnnouncements(Long groupId, Long userId, int page, int size) {
        Page<GroupAnnouncementEntity> pageParam = new Page<>(page, size);
        IPage<GroupAnnouncementEntity> announcementPage = announcementMapper.selectPage(pageParam,
                new LambdaQueryWrapper<GroupAnnouncementEntity>()
                        .eq(GroupAnnouncementEntity::getGroupId, groupId)
                        .orderByDesc(GroupAnnouncementEntity::getCreateTime)
                        .orderByDesc(GroupAnnouncementEntity::getAnnouncementId));
        PageR<GroupAnnouncementListItemResponse> result = new PageR<>(announcementPage.getTotal(), page, size);
        if (announcementPage.getRecords().isEmpty()) {
            return result;
        }

        Set<Long> announcementIds = announcementPage.getRecords().stream()
                .map(GroupAnnouncementEntity::getAnnouncementId)
                .collect(Collectors.toSet());
        Map<Long, List<GroupAnnouncementAttachmentEntity>> attachmentMap = attachmentMapper.selectList(
                        new LambdaQueryWrapper<GroupAnnouncementAttachmentEntity>()
                                .in(GroupAnnouncementAttachmentEntity::getAnnouncementId, announcementIds)
                                .orderByAsc(GroupAnnouncementAttachmentEntity::getSortOrder))
                .stream().collect(Collectors.groupingBy(GroupAnnouncementAttachmentEntity::getAnnouncementId));
        Set<Long> readAnnouncementIds = readMapper.selectList(new LambdaQueryWrapper<GroupAnnouncementReadEntity>()
                        .eq(GroupAnnouncementReadEntity::getUserId, userId)
                        .in(GroupAnnouncementReadEntity::getAnnouncementId, announcementIds)
                        .select(GroupAnnouncementReadEntity::getAnnouncementId))
                .stream().map(GroupAnnouncementReadEntity::getAnnouncementId).collect(Collectors.toSet());
        Map<Long, UserDisplayBase> publisherMap = userService.getUserDisplayInfoByIds(
                announcementPage.getRecords().stream().map(GroupAnnouncementEntity::getPublisherId).collect(Collectors.toSet()));

        result.addAll(announcementPage.getRecords().stream()
                .map(announcement -> buildAnnouncementListItemResponse(announcement, publisherMap,
                        attachmentMap.getOrDefault(announcement.getAnnouncementId(), Collections.emptyList()).size(),
                        readAnnouncementIds.contains(announcement.getAnnouncementId())))
                .toList());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupAnnouncementDetailResponse getAnnouncementDetail(Long groupId, Long announcementId, Long userId) {
        GroupAnnouncementEntity announcement = getAnnouncement(groupId, announcementId);
        LocalDateTime now = LocalDateTime.now();
        readMapper.insertIgnore(GroupAnnouncementReadEntity.builder()
                .id(IdWorker.getId())
                .announcementId(announcementId)
                .userId(userId)
                .readTime(now)
                .createTime(now)
                .build());
        List<GroupAnnouncementAttachmentEntity> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<GroupAnnouncementAttachmentEntity>()
                        .eq(GroupAnnouncementAttachmentEntity::getAnnouncementId, announcementId)
                        .orderByAsc(GroupAnnouncementAttachmentEntity::getSortOrder));
        Map<Long, UserDisplayBase> publisherMap = userService.getUserDisplayInfoByIds(Set.of(announcement.getPublisherId()));
        return buildAnnouncementDetailResponse(announcement, publisherMap, attachments, true);
    }

    @Override
    public String getAttachmentDownloadUrl(Long groupId, Long announcementId, Long attachmentId) {
        getAnnouncement(groupId, announcementId);
        GroupAnnouncementAttachmentEntity attachment = attachmentMapper.selectOne(
                new LambdaQueryWrapper<GroupAnnouncementAttachmentEntity>()
                        .eq(GroupAnnouncementAttachmentEntity::getAttachmentId, attachmentId)
                        .eq(GroupAnnouncementAttachmentEntity::getAnnouncementId, announcementId));
        if (attachment == null) {
            throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_INVALID);
        }
        try {
            String downloadUrl = remoteStorageService.getDownloadUrl(attachment.getObjectKey(), 900L).getData();
            if (downloadUrl == null) {
                throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED);
            }
            return downloadUrl;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("group announcement attachment url get failed. groupId={} announcementId={} attachmentId={}",
                    groupId, announcementId, attachmentId, e);
            throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED, e.getMessage());
        }
    }

    @Override
    public GroupAnnouncementReadStatsResponse getReadStats(Long groupId, Long announcementId, Long operatorUserId) {
        GroupAnnouncementEntity announcement = getAnnouncement(groupId, announcementId);
        assertPublisher(announcement, operatorUserId);
        long memberCount = groupMemberMapper.selectCount(new LambdaQueryWrapper<GroupMemberEntity>()
                .eq(GroupMemberEntity::getGroupId, groupId));
        long readCount = readMapper.countCurrentReadMembers(groupId, announcementId);
        return new GroupAnnouncementReadStatsResponse(readCount, memberCount - readCount);
    }

    @Override
    public PageR<GroupAnnouncementReadMemberResponse> listReadMembers(
            Long groupId, Long announcementId, Long operatorUserId, boolean read, int page, int size) {
        GroupAnnouncementEntity announcement = getAnnouncement(groupId, announcementId);
        assertPublisher(announcement, operatorUserId);
        if (read) {
            IPage<GroupAnnouncementReadEntity> readPage = readMapper.selectReadMemberPage(
                    new Page<>(page, size), groupId, announcementId);
            PageR<GroupAnnouncementReadMemberResponse> result = new PageR<>(readPage.getTotal(), page, size);
            Map<Long, UserDisplayBase> userMap = getUserDisplayInfo(readPage.getRecords().stream()
                    .map(GroupAnnouncementReadEntity::getUserId).collect(Collectors.toSet()));
            result.addAll(readPage.getRecords().stream().map(record -> {
                GroupAnnouncementReadMemberResponse response = new GroupAnnouncementReadMemberResponse();
                response.setUserId(record.getUserId());
                response.setUserInfo(userMap.get(record.getUserId()));
                response.setReadTime(record.getReadTime());
                return response;
            }).toList());
            return result;
        }

        IPage<Long> unreadPage = readMapper.selectUnreadUserIdPage(new Page<>(page, size), groupId, announcementId);
        PageR<GroupAnnouncementReadMemberResponse> result = new PageR<>(unreadPage.getTotal(), page, size);
        Map<Long, UserDisplayBase> userMap = getUserDisplayInfo(new HashSet<>(unreadPage.getRecords()));
        result.addAll(unreadPage.getRecords().stream().map(userId -> {
            GroupAnnouncementReadMemberResponse response = new GroupAnnouncementReadMemberResponse();
            response.setUserId(userId);
            response.setUserInfo(userMap.get(userId));
            return response;
        }).toList());
        return result;
    }

    private Map<String, StorageRecordDTO> validateAttachments(
            Long groupId, List<GroupAnnouncementAttachmentRequest> attachmentRequests) {
        if (attachmentRequests == null || attachmentRequests.isEmpty()) {
            return Collections.emptyMap();
        }
        String requiredPrefix = StorageSceneEnum.PRIVATE_GROUP_ANNOUNCEMENT.getPrefix() + "/" + groupId + "/";
        Set<String> objectKeys = new HashSet<>();
        Map<String, StorageRecordDTO> storageRecordMap = new HashMap<>();
        for (GroupAnnouncementAttachmentRequest attachmentRequest : attachmentRequests) {
            if (!objectKeys.add(attachmentRequest.getObjectKey())
                    || !attachmentRequest.getObjectKey().startsWith(requiredPrefix)) {
                throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_INVALID);
            }
            try {
                StorageRecordDTO storageRecord = remoteStorageService
                        .getFileRecord(attachmentRequest.getObjectKey()).getData();
                if (storageRecord == null || !attachmentRequest.getObjectKey().equals(storageRecord.getObjectKey())) {
                    throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_INVALID);
                }
                storageRecordMap.put(attachmentRequest.getObjectKey(), storageRecord);
            } catch (ServiceException e) {
                throw e;
            } catch (Exception e) {
                log.warn("group announcement attachment validation failed. groupId={} objectKey={}",
                        groupId, attachmentRequest.getObjectKey(), e);
                throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_ATTACHMENT_VALIDATION_FAILED, e.getMessage());
            }
        }
        return storageRecordMap;
    }

    private void replaceAttachments(Long announcementId, List<GroupAnnouncementAttachmentRequest> attachmentRequests,
                                    Map<String, StorageRecordDTO> storageRecords, LocalDateTime now) {
        attachmentMapper.delete(new LambdaQueryWrapper<GroupAnnouncementAttachmentEntity>()
                .eq(GroupAnnouncementAttachmentEntity::getAnnouncementId, announcementId));
        if (attachmentRequests == null || attachmentRequests.isEmpty()) {
            return;
        }
        List<GroupAnnouncementAttachmentEntity> attachments = new java.util.ArrayList<>();
        for (int index = 0; index < attachmentRequests.size(); index++) {
            GroupAnnouncementAttachmentRequest attachmentRequest = attachmentRequests.get(index);
            StorageRecordDTO storageRecord = storageRecords.get(attachmentRequest.getObjectKey());
            attachments.add(GroupAnnouncementAttachmentEntity.builder()
                    .attachmentId(IdWorker.getId())
                    .announcementId(announcementId)
                    .objectKey(attachmentRequest.getObjectKey())
                    .fileName(attachmentRequest.getFileName())
                    .fileSize(storageRecord.getSize())
                    .sortOrder(index)
                    .createTime(now)
                    .build());
        }
        attachmentMapper.insertBatch(attachments);
    }

    private List<Long> listCurrentMemberIds(Long groupId, Long excludedUserId) {
        return groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMemberEntity>()
                        .eq(GroupMemberEntity::getGroupId, groupId)
                        .select(GroupMemberEntity::getUserId))
                .stream()
                .map(GroupMemberEntity::getUserId)
                .filter(userId -> !excludedUserId.equals(userId))
                .toList();
    }

    private GroupAnnouncementEntity getAnnouncement(Long groupId, Long announcementId) {
        GroupAnnouncementEntity announcement = announcementMapper.selectById(announcementId);
        if (announcement == null || !groupId.equals(announcement.getGroupId())) {
            throw new ServiceException(UserError.GROUP_ANNOUNCEMENT_NOT_FOUND);
        }
        return announcement;
    }

    private void assertPublisher(GroupAnnouncementEntity announcement, Long operatorUserId) {
        if (!announcement.getPublisherId().equals(operatorUserId)) {
            throw new PermissionException(PermissionError.PERMISSION_DENIED);
        }
    }

    private Map<Long, UserDisplayBase> getUserDisplayInfo(Set<Long> userIds) {
        return userIds.isEmpty() ? Collections.emptyMap() : userService.getUserDisplayInfoByIds(userIds);
    }

    private GroupAnnouncementListItemResponse buildAnnouncementListItemResponse(
            GroupAnnouncementEntity announcement,
            Map<Long, UserDisplayBase> publisherMap,
            int attachmentCount,
            boolean read) {
        GroupAnnouncementListItemResponse response = BeanUtil.copyProperties(announcement, GroupAnnouncementListItemResponse.class);
        response.setPublisherInfo(publisherMap.get(announcement.getPublisherId()));
        response.setRead(read);
        response.setAttachmentCount(attachmentCount);
        return response;
    }

    private GroupAnnouncementDetailResponse buildAnnouncementDetailResponse(
            GroupAnnouncementEntity announcement,
            Map<Long, UserDisplayBase> publisherMap,
            List<GroupAnnouncementAttachmentEntity> attachments,
            boolean read) {
        GroupAnnouncementDetailResponse response = BeanUtil.copyProperties(announcement, GroupAnnouncementDetailResponse.class);
        response.setPublisherInfo(publisherMap.get(announcement.getPublisherId()));
        response.setRead(read);
        response.setAttachments(attachments.stream().map(attachment -> {
            GroupAnnouncementAttachmentResponse attachmentResponse = new GroupAnnouncementAttachmentResponse();
            attachmentResponse.setAttachmentId(attachment.getAttachmentId());
            attachmentResponse.setFileName(attachment.getFileName());
            attachmentResponse.setFileSize(attachment.getFileSize());
            attachmentResponse.setSortOrder(attachment.getSortOrder());
            return attachmentResponse;
        }).toList());
        return response;
    }
}
