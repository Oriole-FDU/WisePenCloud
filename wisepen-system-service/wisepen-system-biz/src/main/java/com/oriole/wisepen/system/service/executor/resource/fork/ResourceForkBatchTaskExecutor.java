package com.oriole.wisepen.system.service.executor.resource.fork;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.note.api.domain.dto.NoteBatchForkReqDTO;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.feign.RemoteNoteService;
import com.oriole.wisepen.resource.domain.dto.ResourceItemInfoResDTO;
import com.oriole.wisepen.resource.domain.dto.req.PersonalFolderEnsureReqDTO;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import com.oriole.wisepen.system.api.enums.BatchTaskItemStatus;
import com.oriole.wisepen.system.api.enums.BatchTaskType;
import com.oriole.wisepen.system.domain.entity.BatchTaskEntity;
import com.oriole.wisepen.system.domain.entity.BatchTaskItemEntity;
import com.oriole.wisepen.system.excpetion.BatchTaskError;
import com.oriole.wisepen.system.excpetion.SysError;
import com.oriole.wisepen.system.service.batch.BatchTaskExecutor;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ResourceForkBatchTaskExecutor implements BatchTaskExecutor {

    private static final int USER_QUERY_BATCH_SIZE = 500;

    private final ObjectMapper objectMapper;
    private final RemoteResourceService remoteResourceService;
    private final RemoteNoteService remoteNoteService;
    private final RemoteUserService remoteUserService;

    @Override
    public BatchTaskType getType() {
        return BatchTaskType.RESOURCE_FORK;
    }

    @Override
    public List<BatchTaskItemEntity> expandTask(BatchTaskEntity task) {
        ResourceForkTaskMeta taskMeta;
        try {
            taskMeta = objectMapper.readValue(task.getMeta(), ResourceForkTaskMeta.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException(
                    SysError.BATCH_TASK_META_INVALID, "RESOURCE_FORK meta 数据无法解析，请检查字段类型和枚举值");
        }

        if (taskMeta.getTargetUserIds() == null || taskMeta.getTargetUserIds().isEmpty()) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID, "targetUserIds 不能为空");
        }

        // 去除空 targetUserId
        List<Long> targetUserIds = taskMeta.getTargetUserIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        if (targetUserIds.isEmpty()) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID, "targetUserIds 不能为空");
        }

        List<String> folderPath = new ArrayList<>();
        if (taskMeta.getTargetFolderPath() != null) {
            for (String segment : taskMeta.getTargetFolderPath()) {
                // 校验路径非空，去除文本中多余空格
                if (!StringUtils.hasText(segment)) {
                    throw new ServiceException(SysError.BATCH_TASK_META_INVALID, "targetFolderPath 不能包含空路径");
                }
                folderPath.add(segment.trim());
            }
        }
        taskMeta.setTargetFolderPath(folderPath);
        if (taskMeta.getCreateFolderIfAbsent() == null) {
            taskMeta.setCreateFolderIfAbsent(true);
        }

        // 批量检查用户
        Set<Long> validUserIds = new LinkedHashSet<>();
        for (int start = 0; start < targetUserIds.size(); start += USER_QUERY_BATCH_SIZE) {
            // 分批请求 getUserDisplayInfo 接口检查用户
            List<Long> userIdBatch = targetUserIds.subList(
                    start, Math.min(start + USER_QUERY_BATCH_SIZE, targetUserIds.size()));
            Map<Long, UserDisplayBase> userMap = remoteUserService.getUserDisplayInfo(userIdBatch).getData();
            if (userMap != null) {
                validUserIds.addAll(userMap.keySet());
            }
        }
        taskMeta.setTargetUserIds(targetUserIds);

        // 校验资源是否存在
        List<ResourceItemInfoResDTO> resources = remoteResourceService
                .listResourceBaseInfo(List.of(taskMeta.getSourceResourceId())).getData();
        if (resources == null) throw new ServiceException(BatchTaskError.RESOURCE_FORK_SOURCE_NOT_FOUND);

        ResourceItemInfoResDTO sourceResource = resources.getFirst();

        Integer currentVersion = switch (sourceResource.getResourceType()) {
            case NOTE, DRAWIO -> {
                NoteSnapshotResponse snapshot = remoteNoteService
                        .getNoteLatestVersion(sourceResource.getResourceId()).getData();
                yield snapshot == null ? null : snapshot.getVersion();
            }
            // TODO: 在未来支持更多类型资源的 Fork，暂时仅支持 NOTE 和 DRAWIO
            default -> throw new ServiceException(BatchTaskError.RESOURCE_FORK_TYPE_UNSUPPORTED);
        };

        if (currentVersion == null) throw new ServiceException(BatchTaskError.RESOURCE_FORK_SOURCE_NOT_FOUND);
        Integer sourceVersion = taskMeta.getSourceVersion() == null ? currentVersion : taskMeta.getSourceVersion();
        // 版本配置检查
        if (sourceVersion > currentVersion) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID, "sourceVersion 不能大于资源最新版本");
        }
        taskMeta.setSourceVersion(sourceVersion);

        taskMeta.setTargetResourceName(StringUtils.hasText(taskMeta.getTargetResourceName())
                ? taskMeta.getTargetResourceName().trim() : sourceResource.getResourceName());

        List<BatchTaskItemEntity> items = new ArrayList<>(targetUserIds.size());
        try {
            for (Long userId : targetUserIds) {
                boolean validUser = validUserIds.contains(userId);
                ResourceForkTaskMeta itemMeta = BeanUtil.copyProperties(taskMeta, ResourceForkTaskMeta.class);
                itemMeta.setTargetUserIds(List.of(userId)); // 子任务是对单用户的

                BatchTaskItemEntity item = BatchTaskItemEntity.builder()
                        .meta(objectMapper.writeValueAsString(itemMeta))
                        // 用户不存在直接失败对应的子任务
                        .status(validUser ? BatchTaskItemStatus.PENDING : BatchTaskItemStatus.FAILED)
                        .errorCode(validUser ? null : SysError.BATCH_TASK_TARGET_USER_NOT_FOUND.getCode())
                        .errorMessage(validUser ? null : SysError.BATCH_TASK_TARGET_USER_NOT_FOUND.getMsg())
                        .build();
                items.add(item);
            }
            return items;
        } catch (JsonProcessingException e) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID, "RESOURCE_FORK 任务项 meta 序列化出错");
        }
    }

    @Override
    public BatchTaskItemEntity executeItem(BatchTaskItemEntity taskItem) {
        ResourceForkTaskMeta taskMeta;
        try {
            taskMeta = objectMapper.readValue(taskItem.getMeta(), ResourceForkTaskMeta.class);

            long targetUserId = taskMeta.getTargetUserIds().get(0);

            String targetTagId = null;
            if (taskMeta.getTargetFolderPath() != null && !taskMeta.getTargetFolderPath().isEmpty()) {
                // 需要先确保目标文件夹存在
                PersonalFolderEnsureReqDTO folderRequest = new PersonalFolderEnsureReqDTO();

                BeanUtil.copyProperties(
                        taskMeta, folderRequest,
                        CopyOptions.create().setFieldMapping(Map.of(
                                "targetFolderPath", "folderPath",
                                "createFolderIfAbsent", "createIfAbsent")));
                folderRequest.setTargetUserId(Long.toString(targetUserId));
                // 请求 Resource 微服务
                targetTagId = remoteResourceService.ensurePersonalFolder(folderRequest).getData();
                if (!StringUtils.hasText(targetTagId)) {
                    throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED);
                }
            }
            String targetResourceId = null;
            switch (taskMeta.getSourceResourceType()){
                case NOTE:
                case DRAWIO:
                    NoteBatchForkReqDTO forkRequest = new NoteBatchForkReqDTO();
                    BeanUtil.copyProperties(
                            taskMeta, forkRequest,
                            CopyOptions.create().setFieldMapping(Map.of(
                                    "sourceResourceId", "resourceId",
                                    "sourceVersion", "forkedResourceVersion",
                                    "targetResourceName", "forkedResourceName")));
                    forkRequest.setTargetOwnerUserId(Long.toString(targetUserId));
                    forkRequest.setMountTargetTagId(targetTagId);
                    // 请求 Note 微服务
                    targetResourceId = remoteNoteService.forkNote(forkRequest).getData();
                    break;
                default:
                    // TODO: 在未来支持更多类型资源的Fork，暂时仅支持NOTE和DRAWIO
                    throw new ServiceException(BatchTaskError.RESOURCE_FORK_TYPE_UNSUPPORTED);
            }

            if (!StringUtils.hasText(targetResourceId)) {
                throw new ServiceException(SysError.BATCH_TASK_ITEM_EXECUTION_FAILED);
            }

            ObjectNode result = objectMapper.createObjectNode()
                    .put("targetUserId", targetUserId)
                    .put("targetResourceId", targetResourceId);
            if (StringUtils.hasText(targetTagId)) result.put("targetTagId", targetTagId);
            taskItem.setResult(objectMapper.writeValueAsString(result));
            return taskItem;
        } catch (JsonProcessingException e) {
            throw new ServiceException(SysError.BATCH_TASK_META_INVALID, "RESOURCE_FORK 任务项 meta 解析出错");
        }
    }
}
