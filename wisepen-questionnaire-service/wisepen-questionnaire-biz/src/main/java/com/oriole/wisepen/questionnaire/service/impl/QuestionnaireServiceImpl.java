package com.oriole.wisepen.questionnaire.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireDraftUpdateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireForkRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireVersionInfoResponse;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnairePageDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnairePageDefinition.QuestionnaireColumnItem;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireSubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import com.oriole.wisepen.questionnaire.api.enums.TableVersionStatus;
import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;
import com.oriole.wisepen.questionnaire.domain.entity.TableVersionEntity;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import com.oriole.wisepen.questionnaire.repository.TableRepository;
import com.oriole.wisepen.questionnaire.repository.TableRowRepository;
import com.oriole.wisepen.questionnaire.repository.TableVersionRepository;
import com.oriole.wisepen.questionnaire.service.IQuestionnaireService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireServiceImpl implements IQuestionnaireService {

    private final TableRepository tableRepository;
    private final TableVersionRepository tableVersionRepository;
    private final TableRowRepository tableRowRepository;
    private final RemoteResourceService remoteResourceService;

    @Transactional
    @Override
    public String createQuestionnaire(QuestionnaireCreateRequest request, String userId, Map<Long, GroupRoleType> groupRoles) {
        // 向资源服务注册 QUESTIONNAIRE 类型资源
        String resourceId;
        try {
            resourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                    .resourceName(request.getTitle())
                    .resourceType(ResourceType.QUESTIONNAIRE)
                    .ownerId(userId)
                    .ownerGroupRoles(groupRoles)
                    .mountTargetTagId(request.getMountTargetTagId())
                    .build()).getData();
        } catch (Exception e) {
            log.error("questionnaire resource register failed. userId={} dependency=resourceService", userId, e);
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_REGISTER_RESOURCE_FAILED);
        }

        try {
            // version=0 表示当前尚无已发布版本
            tableRepository.save(TableEntity.builder()
                    .resourceId(resourceId)
                    .version(0)
                    .authors(new ArrayList<>(List.of(Long.valueOf(userId))))
                    .build());
            // 初始化 version=1 的完整空白草稿，结构和提交策略随版本一起保存。
            tableVersionRepository.save(TableVersionEntity.builder()
                    .resourceId(resourceId)
                    .version(1)
                    .status(TableVersionStatus.DRAFT)
                    .description(request.getDescription())
                    .columns(new ArrayList<>())
                    .pages(new ArrayList<>())
                    .submissionPolicy(QuestionnaireSubmissionPolicy.builder()
                            .anonymous(false)
                            .maxSubmissionsPerUser(1)
                            .draftAllowed(true)
                            .editableAfterSubmit(false)
                            .build())
                    .build());
        } catch (Exception e) {
            // TODO: RemoteResourceService 增加内部撤销注册接口后，在此撤销已注册的问卷资源
            log.error("questionnaire initialization failed. resourceId={}", resourceId, e);
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_INITIALIZATION_FAILED);
        }
        return resourceId;
    }

    @Override
    public QuestionnaireVersionInfoResponse getQuestionnaireDraft(String resourceId, Integer draftVersion) {
        TableVersionEntity draft = tableVersionRepository.findByResourceIdAndVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));
        return BeanUtil.copyProperties(draft, QuestionnaireVersionInfoResponse.class);
    }

    @Transactional
    @Override
    public void updateQuestionnaireDraft(QuestionnaireDraftUpdateRequest request) {
        TableEntity table = tableRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
        // 草稿固定使用当前发布版本的下一版本，请求必须指向该草稿版本
        int draftVersion = table.getVersion() + 1;
        // 请求必须指向当前草稿版本, 若编辑期间原草稿已完成发布，则拒绝旧版本更新; 同一草稿版本仍允许相互覆盖
        if (request.getVersion() != draftVersion) {
            throw new ServiceException(QuestionnaireError.TABLE_VERSION_CONFLICT);
        }
        TableVersionEntity draft = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), draftVersion)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));

        // 校验当前版本的题目定义：columnId 用于关联答案和页面位置，必须存在且唯一。
        Set<String> columnIds = new HashSet<>();
        for (TableColumn column : request.getColumns()) {
            if (column == null || column.getColumnId() == null || column.getColumnId().isBlank()) {
                throw new ServiceException(QuestionnaireError.TABLE_COLUMN_INVALID, "列ID不能为空");
            }
            if (!columnIds.add(column.getColumnId())) {
                throw new ServiceException(QuestionnaireError.TABLE_COLUMN_DUPLICATED);
            }
            try {
                column.validateDefinition();
                if (column.getDefaultValue() != null) {
                    column.validateValue(column.getDefaultValue());
                }
            } catch (IllegalArgumentException e) {
                throw new ServiceException(QuestionnaireError.TABLE_COLUMN_INVALID, e.getMessage());
            }
        }

        // pages 仅负责题目布局：每个 columnId 必须存在于 columns，且在所有页面中只能被引用一次。
        if (request.getPages() != null) {
            Set<String> pageColumnIds = new HashSet<>();
            for (QuestionnairePageDefinition page : request.getPages()) {
                for (QuestionnaireColumnItem item : page.getItems()) {
                    if (!columnIds.contains(item.getColumnId())) {
                        throw new ServiceException(QuestionnaireError.TABLE_COLUMN_INVALID,
                                "页面引用的列不存在: " + item.getColumnId());
                    }
                    if (!pageColumnIds.add(item.getColumnId())) {
                        throw new ServiceException(QuestionnaireError.TABLE_COLUMN_DUPLICATED);
                    }
                }
            }
        }

        // 起止时间均可缺省；同时设置时，开始时间不得晚于结束时间
        QuestionnaireSubmissionPolicy policy = request.getSubmissionPolicy();
        if (policy.getStartTime() != null && policy.getEndTime() != null && policy.getStartTime().isAfter(policy.getEndTime())) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_POLICY_INVALID, "submission time range invalid");
        }

        draft.setCompletionMessage(request.getCompletionMessage());
        draft.setDescription(request.getDescription());
        draft.setSubmissionPolicy(request.getSubmissionPolicy());
        draft.setColumns(request.getColumns());
        draft.setPages(request.getPages());
        tableVersionRepository.save(draft);
    }

    @Transactional
    @Override
    public void publishQuestionnaireVersion(String resourceId) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
        // 主档 version 指向当前已发布版本，因此唯一草稿固定为下一版本
        int draftVersion = table.getVersion() + 1;
        TableVersionEntity draft = tableVersionRepository.findByResourceIdAndVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));

        draft.checkCoreDefinitionReady();

        int nextDraftVersion = draftVersion + 1;
        try {
            draft.setStatus(TableVersionStatus.PUBLISHED);
            tableVersionRepository.save(draft);

            tableRepository.updateVersionByResourceId(resourceId, draftVersion);

            // 复制本次发布内容，作为下一版本的可编辑草稿
            tableVersionRepository.save(TableVersionEntity.builder()
                    .resourceId(resourceId)
                    .version(nextDraftVersion)
                    .status(TableVersionStatus.DRAFT)
                    .description(draft.getDescription())
                    .completionMessage(draft.getCompletionMessage())
                    .submissionPolicy(draft.getSubmissionPolicy())
                    .columns(draft.getColumns())
                    .pages(draft.getPages())
                    .build());

            log.info("questionnaire version status changed. resourceId={} version={} from={} to={} nextDraftVersion={}",
                    resourceId, draftVersion, TableVersionStatus.DRAFT, TableVersionStatus.PUBLISHED, nextDraftVersion);
        } catch (Exception e) {
            log.error("questionnaire publish failed. resourceId={} version={}", resourceId, draftVersion, e);
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_PUBLISH_FAILED);
        }
    }

    @Override
    public QuestionnaireVersionInfoResponse getQuestionnaire(String resourceId, Integer version) {
        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(resourceId, version)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));
        if (tableVersion.getStatus() != TableVersionStatus.PUBLISHED) {
            throw new ServiceException(QuestionnaireError.TABLE_VERSION_STATUS_INVALID);
        }

        return BeanUtil.copyProperties(tableVersion, QuestionnaireVersionInfoResponse.class);
    }

    @Transactional
    @Override
    public String forkQuestionnaire(QuestionnaireForkRequest request, String forkedResourceOwnerId) {
        Integer sourceVersion = request.getForkedResourceVersion();
        if (sourceVersion == null) {
            // 未指定版本时，复制问卷主档记录的当前最新发布版本。
            TableEntity sourceTable = tableRepository.findById(request.getResourceId())
                    .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
            sourceVersion = sourceTable.getVersion();
        }

        TableVersionEntity sourceTableVersion = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), sourceVersion)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));
        // fork 只接受已发布内容，避免将源问卷尚未完成的草稿复制出去
        if (sourceTableVersion.getStatus() != TableVersionStatus.PUBLISHED) {
            throw new ServiceException(QuestionnaireError.TABLE_VERSION_STATUS_INVALID);
        }

        String targetResourceId;
        try {
            targetResourceId = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                    .resourceName(request.getForkedResourceName())
                    .resourceType(ResourceType.QUESTIONNAIRE)
                    .ownerId(forkedResourceOwnerId)
                    .build()).getData();
        } catch (Exception e) {
            log.error("questionnaire resource register failed. sourceResourceId={} ownerId={} dependency=resourceService",
                    request.getResourceId(), forkedResourceOwnerId, e);
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_REGISTER_RESOURCE_FAILED);
        }

        try {
            // 源发布内容作为新问卷的首个发布版本，并据此初始化下一版草稿。
            tableRepository.save(TableEntity.builder()
                    .resourceId(targetResourceId)
                    .version(1)
                    .authors(new ArrayList<>(List.of(Long.valueOf(forkedResourceOwnerId))))
                    .build());
            tableVersionRepository.save(TableVersionEntity.builder()
                    .resourceId(targetResourceId)
                    .version(1)
                    .status(TableVersionStatus.PUBLISHED)
                    .description(sourceTableVersion.getDescription())
                    .completionMessage(sourceTableVersion.getCompletionMessage())
                    .submissionPolicy(sourceTableVersion.getSubmissionPolicy())
                    .columns(sourceTableVersion.getColumns())
                    .pages(sourceTableVersion.getPages())
                    .build());
            tableVersionRepository.save(TableVersionEntity.builder()
                    .resourceId(targetResourceId)
                    .version(2)
                    .status(TableVersionStatus.DRAFT)
                    .description(sourceTableVersion.getDescription())
                    .completionMessage(sourceTableVersion.getCompletionMessage())
                    .submissionPolicy(sourceTableVersion.getSubmissionPolicy())
                    .columns(sourceTableVersion.getColumns())
                    .pages(sourceTableVersion.getPages())
                    .build());

            log.info("questionnaire fork finished. sourceResourceId={} sourceVersion={} resourceId={}", request.getResourceId(), sourceVersion, targetResourceId);
            return targetResourceId;
        } catch (Exception e) {
            // TODO: RemoteResourceService 增加内部撤销注册接口后，删除已创建的目标资源
            log.error("questionnaire fork failed. sourceResourceId={} sourceVersion={} resourceId={}", request.getResourceId(), sourceVersion, targetResourceId, e);
            throw new ServiceException(QuestionnaireError.QUESTIONNAIRE_FORK_FAILED);
        }
    }

    @Transactional
    @Override
    public void deleteQuestionnaires(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        // 清理问卷主档、包含完整结构的所有版本及答卷。
        tableRepository.deleteByResourceIdIn(resourceIds);
        tableVersionRepository.deleteByResourceIdIn(resourceIds);
        tableRowRepository.deleteByResourceIdIn(resourceIds);
    }

    @Override
    public TableEntity getQuestionnaireInfo(String resourceId) {
        return tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
    }
}
