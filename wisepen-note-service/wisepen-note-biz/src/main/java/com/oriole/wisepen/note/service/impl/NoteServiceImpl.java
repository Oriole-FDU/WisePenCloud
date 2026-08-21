package com.oriole.wisepen.note.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.note.api.domain.dto.req.NoteCreateRequest;
import com.oriole.wisepen.note.api.domain.dto.req.NoteForkRequest;
import com.oriole.wisepen.note.api.domain.enums.VersionType;
import com.oriole.wisepen.note.domain.entity.NoteContentEntity;
import com.oriole.wisepen.note.domain.entity.NoteInfoEntity;
import com.oriole.wisepen.note.domain.entity.NoteVersionEntity;
import com.oriole.wisepen.note.exception.NoteError;
import com.oriole.wisepen.note.repository.NoteInfoRepository;
import com.oriole.wisepen.note.repository.NoteVersionRepository;
import com.oriole.wisepen.note.repository.NoteContentRepository;
import com.oriole.wisepen.note.service.INoteOperationLogService;
import com.oriole.wisepen.note.service.INoteService;
import com.oriole.wisepen.note.service.INoteVersionService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements INoteService {

    private final NoteInfoRepository noteInfoRepository;

    private final NoteVersionRepository noteVersionRepository;
    private final NoteContentRepository noteContentRepository;
    private final INoteVersionService noteVersionService;
    private final INoteOperationLogService noteOperationLogService;
    private final RemoteResourceService remoteResourceService;

    @Override
    public String createNote(NoteCreateRequest request, String userId, Map<Long, GroupRoleType> groupRoles) {
        ResourceType resourceType = request.getResourceType() == null ? ResourceType.NOTE : request.getResourceType();
        // 检查资源类型
        if (resourceType != ResourceType.NOTE && resourceType != ResourceType.DRAWIO) {
            throw new ServiceException(NoteError.CANNOT_SUPPORT_NOTE_RESOURCE_TYPE);
        }

        // 向 resource 服务注册Note资源
        String resourceId;
        try {
            resourceId = remoteResourceService.createResource(
                    ResourceCreateReqDTO.builder()
                            .resourceName(request.getTitle())
                            .resourceType(resourceType)
                            .ownerId(userId)
                            .ownerGroupRoles(groupRoles)
                            .mountTargetTagId(request.getMountTargetTagId())
                            .build()
            ).getData();
        } catch (Exception e) {
            log.error("note resource register failed. dependency=resourceService", e);
            throw new ServiceException(NoteError.NOTE_REGISTER_RESOURCE_FAILED, e.getMessage());
        }

        List<Long> authors = new ArrayList<>();
        authors.add(Long.valueOf(userId));

        NoteInfoEntity infoEntity = NoteInfoEntity.builder()
                .resourceId(resourceId)
                .resourceType(resourceType)
                .version(0)
                .authors(authors)
                .build();
        noteInfoRepository.save(infoEntity);
        return resourceId;
    }

    @Override
    @Transactional
    public void deleteNotes(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        // 移除所有内容
        noteInfoRepository.deleteByResourceIdIn(resourceIds);
        noteContentRepository.deleteByResourceIdIn(resourceIds);
        noteVersionService.deleteAllVersionsByResourceIds(resourceIds);
        noteOperationLogService.deleteAllOpLogsByResourceIds(resourceIds);
    }

    @Override
    public NoteInfoEntity getNoteInfo(String resourceId) {
        return noteInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(NoteError.NOTE_NOT_FOUND));
    }

    @Override
    @Transactional
    public String forkNote(NoteForkRequest request, String forkedResourceOwnerId) {
        NoteInfoEntity sourceInfo = noteInfoRepository.findByResourceId(request.getResourceId())
                .orElseThrow(() -> new ServiceException(NoteError.NOTE_NOT_FOUND));

        List<NoteVersionEntity> sourceVersions = new ArrayList<>();

        // 向 resource 服务注册 Forked 资源
        String targetResourceId;
        try {
            targetResourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                    .resourceName(request.getForkedResourceName())
                    .resourceType(sourceInfo.getResourceType())
                    .ownerId(forkedResourceOwnerId)
                    .build()
            ).getData();
        } catch (Exception e) {
            log.error("note resource register failed. dependency=resourceService", e);
            throw new ServiceException(NoteError.NOTE_REGISTER_RESOURCE_FAILED, e.getMessage());
        }

        try {
            // 建立文档元信息
            NoteInfoEntity targetInfo = NoteInfoEntity.builder()
                    .resourceId(targetResourceId)
                    .authors(sourceInfo.getAuthors())
                    .resourceType(sourceInfo.getResourceType())
                    .version(0)
                    .build();
            noteInfoRepository.save(targetInfo);

            Integer forkedResourceVersion = request.getForkedResourceVersion() == null ? sourceInfo.getVersion() : request.getForkedResourceVersion();

            // 复制 笔记内容
            // 查询指定资源在指定版本号（含）之前的最新 FULL 版本记录
            Optional<NoteVersionEntity> latestFull = noteVersionRepository
                    .findFirstByResourceIdAndTypeAndVersionLessThanEqualOrderByVersionDesc(
                            request.getResourceId(), VersionType.FULL, forkedResourceVersion);

            Integer latestFullVersion = 0;
            if (latestFull.isPresent()) { // 存在这样的 FULL 版本
                sourceVersions.add(latestFull.get());
                latestFullVersion = latestFull.get().getVersion();
            }
            // 查询指定资源在指定版本号区间内（从当前版本到最近的 FULL 版本）的所有 DELTA 版本记录，并按版本号升序排列
            // 如果没有最近的 FULL 版本，则到 0
            sourceVersions.addAll(noteVersionRepository
                    .findByResourceIdAndVersionRangeAndTypeOrderByVersionAsc(
                            request.getResourceId(), latestFullVersion, forkedResourceVersion, VersionType.DELTA));

            Integer baseVersion = latestFullVersion - 1;
            List<NoteVersionEntity> targetVersions = new ArrayList<>();
            if (!sourceVersions.isEmpty()) {
                for (NoteVersionEntity sourceVersion : sourceVersions) {
                    NoteVersionEntity targetVersion = BeanUtil.copyProperties(sourceVersion, NoteVersionEntity.class, "createTime");
                    targetVersion.setId(null);
                    targetVersion.setResourceId(targetResourceId);
                    if (baseVersion > 0) { // 重置版本号为自 1 起始
                        targetVersion.setVersion(targetVersion.getVersion() - baseVersion);
                    }
                    targetVersions.add(targetVersion);
                }
                noteVersionRepository.saveAll(targetVersions);
            }

            // 如果存在最近的 FULL 版本，则还需要复制 Content
            if (latestFullVersion != 0) {
                noteContentRepository.findById(latestFull.get().getResourceId()).ifPresent(sourceContent -> {
                    NoteContentEntity targetContent = BeanUtil.copyProperties(sourceContent, NoteContentEntity.class);
                    targetContent.setResourceId(targetResourceId);
                    if (baseVersion > 0) { // 重置版本号为自 1 起始
                        targetContent.setVersion(targetContent.getVersion() - baseVersion);
                    }
                    noteContentRepository.save(targetContent);
                });
            }

            Integer maxVersion = targetVersions.stream().map(NoteVersionEntity::getVersion).max(Integer::compareTo).orElse(0);
            noteInfoRepository.updateVersionByResourceId(targetResourceId, maxVersion);

            log.info("note fork finished. sourceResourceId={} resourceId={} version={}",
                    request.getResourceId(), targetResourceId, forkedResourceVersion);

            return targetResourceId;
        } catch (Exception e) {
            // 异常时回滚
            noteInfoRepository.deleteByResourceIdIn(List.of(targetResourceId));
            noteVersionRepository.deleteByResourceIdIn(List.of(targetResourceId));
            noteContentRepository.deleteByResourceIdIn(List.of(targetResourceId));
            log.warn("note fork compensated. sourceResourceId={} resourceId={}",
                    request.getResourceId(), targetResourceId, e);
            throw new ServiceException(NoteError.NOTE_FORK_FAILED, e.getMessage());
        }
    }
}
