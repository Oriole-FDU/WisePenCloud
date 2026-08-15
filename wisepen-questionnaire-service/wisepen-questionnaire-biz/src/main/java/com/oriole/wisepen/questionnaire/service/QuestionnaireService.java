package com.oriole.wisepen.questionnaire.service;

import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireDraftUpdateRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmitRequest;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireDefinitionResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionResponse;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireColumnItem;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireViewDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import com.oriole.wisepen.questionnaire.api.enums.SubmissionStatus;
import com.oriole.wisepen.questionnaire.api.enums.TableVersionStatus;
import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireViewEntity;
import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;
import com.oriole.wisepen.questionnaire.domain.entity.TableRowEntity;
import com.oriole.wisepen.questionnaire.domain.entity.TableVersionEntity;
import com.oriole.wisepen.questionnaire.exception.TableError;
import com.oriole.wisepen.questionnaire.repository.QuestionnaireViewRepository;
import com.oriole.wisepen.questionnaire.repository.TableRepository;
import com.oriole.wisepen.questionnaire.repository.TableRowRepository;
import com.oriole.wisepen.questionnaire.repository.TableVersionRepository;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.domain.dto.ResourceUpdateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionnaireService {
    private static final int FIRST_DRAFT_VERSION = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final TableRepository tableRepository;
    private final TableVersionRepository tableVersionRepository;
    private final QuestionnaireViewRepository questionnaireViewRepository;
    private final TableRowRepository tableRowRepository;
    private final RemoteResourceService remoteResourceService;

    @Transactional
    public String createQuestionnaire(QuestionnaireCreateRequest request, Long userId, Map<Long, GroupRoleType> groupRoles) {
        R<String> createdResource = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                .resourceName(request.getTitle())
                .resourceType(ResourceType.QUESTIONNAIRE)
                .ownerId(userId.toString())
                .ownerGroupRoles(groupRoles)
                .mountTargetTagId(request.getMountTargetTagId())
                .preview(request.getDescription())
                .build());
        String resourceId = createdResource == null ? null : createdResource.getData();
        if (!StringUtils.hasText(resourceId)) {
            throw new ServiceException(TableError.TABLE_REGISTER_RESOURCE_FAILED);
        }

        tableRepository.save(TableEntity.builder()
                .resourceId(resourceId)
                .version(0)
                .title(request.getTitle())
                .description(request.getDescription())
                .build());

        tableVersionRepository.save(TableVersionEntity.builder()
                .resourceId(resourceId)
                .version(FIRST_DRAFT_VERSION)
                .status(TableVersionStatus.DRAFT)
                .columns(new ArrayList<>())
                .build());
        questionnaireViewRepository.save(QuestionnaireViewEntity.builder()
                .resourceId(resourceId)
                .tableVersion(FIRST_DRAFT_VERSION)
                .definition(QuestionnaireViewDefinition.builder()
                        .title(request.getTitle())
                        .description(request.getDescription())
                        .pages(new ArrayList<>())
                        .completionMessage(null)
                        .submissionPolicy(defaultSubmissionPolicy())
                        .build())
                .build());
        return resourceId;
    }

    @Transactional
    public void updateDraftDefinition(QuestionnaireDraftUpdateRequest request) {
        TableEntity table = tableRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        int draftVersion = table.getVersion() + 1;
        TableVersionEntity draft = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), draftVersion)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (draft.getStatus() != TableVersionStatus.DRAFT) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }

        draft.setColumns(new ArrayList<>(request.getColumns()));
        tableVersionRepository.save(draft);

        QuestionnaireViewEntity view = questionnaireViewRepository.findByResourceIdAndTableVersion(request.getResourceId(), draftVersion)
                .orElseGet(() -> QuestionnaireViewEntity.builder()
                        .resourceId(request.getResourceId())
                        .tableVersion(draftVersion)
                        .build());
        view.setDefinition(request.getViewDefinition());
        questionnaireViewRepository.save(view);

        boolean tableChanged = false;
        if (StringUtils.hasText(request.getTitle())) {
            table.setTitle(request.getTitle());
            tableChanged = true;
        }
        if (request.getDescription() != null) {
            table.setDescription(request.getDescription());
            tableChanged = true;
        }
        if (tableChanged) {
            tableRepository.save(table);
            remoteResourceService.updateAttributes(ResourceUpdateReqDTO.builder()
                    .resourceId(request.getResourceId())
                    .resourceName(table.getTitle())
                    .preview(table.getDescription())
                    .build());
        }
    }

    @Transactional
    public void publishQuestionnaireVersion(String resourceId) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        int draftVersion = table.getVersion() + 1;
        TableVersionEntity draft = tableVersionRepository.findByResourceIdAndVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (draft.getStatus() != TableVersionStatus.DRAFT) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }
        QuestionnaireViewEntity draftView = questionnaireViewRepository.findByResourceIdAndTableVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(TableError.QUESTIONNAIRE_VIEW_NOT_FOUND));

        validateTableDefinition(draft.getColumns());
        validateQuestionnaireView(draftView.getDefinition(), draft.getColumns());

        draft.setStatus(TableVersionStatus.PUBLISHED);
        tableVersionRepository.save(draft);
        tableRepository.updateVersionByResourceId(resourceId, draftVersion);

        int nextDraftVersion = draftVersion + 1;
        tableVersionRepository.save(TableVersionEntity.builder()
                .resourceId(resourceId)
                .version(nextDraftVersion)
                .status(TableVersionStatus.DRAFT)
                .columns(new ArrayList<>(draft.getColumns()))
                .build());
        questionnaireViewRepository.save(QuestionnaireViewEntity.builder()
                .resourceId(resourceId)
                .tableVersion(nextDraftVersion)
                .definition(draftView.getDefinition())
                .build());
    }

    public QuestionnaireDefinitionResponse getQuestionnaire(String resourceId, Integer version) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        Integer tableVersionNumber = version == null ? table.getVersion() : version;
        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(resourceId, tableVersionNumber)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (tableVersion.getStatus() != TableVersionStatus.PUBLISHED) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }
        QuestionnaireViewEntity view = questionnaireViewRepository.findByResourceIdAndTableVersion(resourceId, tableVersion.getVersion())
                .orElseThrow(() -> new ServiceException(TableError.QUESTIONNAIRE_VIEW_NOT_FOUND));

        return QuestionnaireDefinitionResponse.builder()
                .resourceId(table.getResourceId())
                .version(tableVersion.getVersion())
                .status(tableVersion.getStatus())
                .title(table.getTitle())
                .description(table.getDescription())
                .columns(tableVersion.getColumns())
                .viewDefinition(view.getDefinition())
                .build();
    }

    public QuestionnaireDefinitionResponse getTable(String resourceId, Integer version, boolean allowDraft) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        Integer tableVersionNumber = version == null ? table.getVersion() : version;
        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(resourceId, tableVersionNumber)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (tableVersion.getStatus() == TableVersionStatus.DRAFT && !allowDraft) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }
        QuestionnaireViewEntity view = questionnaireViewRepository.findByResourceIdAndTableVersion(resourceId, tableVersion.getVersion())
                .orElseThrow(() -> new ServiceException(TableError.QUESTIONNAIRE_VIEW_NOT_FOUND));

        return QuestionnaireDefinitionResponse.builder()
                .resourceId(table.getResourceId())
                .version(tableVersion.getVersion())
                .status(tableVersion.getStatus())
                .title(table.getTitle())
                .description(table.getDescription())
                .columns(tableVersion.getColumns())
                .viewDefinition(view.getDefinition())
                .build();
    }

    public QuestionnaireDefinitionResponse getDraftTable(String resourceId) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        int draftVersion = table.getVersion() + 1;
        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (tableVersion.getStatus() != TableVersionStatus.DRAFT) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }
        QuestionnaireViewEntity view = questionnaireViewRepository.findByResourceIdAndTableVersion(resourceId, draftVersion)
                .orElseThrow(() -> new ServiceException(TableError.QUESTIONNAIRE_VIEW_NOT_FOUND));

        return QuestionnaireDefinitionResponse.builder()
                .resourceId(table.getResourceId())
                .version(tableVersion.getVersion())
                .status(tableVersion.getStatus())
                .title(table.getTitle())
                .description(table.getDescription())
                .columns(tableVersion.getColumns())
                .viewDefinition(view.getDefinition())
                .build();
    }

    @Transactional
    public QuestionnaireSubmissionResponse submitQuestionnaire(QuestionnaireSubmitRequest request, Long userId) {
        TableEntity table = tableRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        Integer tableVersionNumber = request.getVersion() == null ? table.getVersion() : request.getVersion();
        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), tableVersionNumber)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (tableVersion.getStatus() != TableVersionStatus.PUBLISHED) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }
        QuestionnaireViewEntity view = questionnaireViewRepository.findByResourceIdAndTableVersion(request.getResourceId(), tableVersion.getVersion())
                .orElseThrow(() -> new ServiceException(TableError.QUESTIONNAIRE_VIEW_NOT_FOUND));

        SubmissionStatus status = request.getStatus() == null ? SubmissionStatus.SUBMITTED : request.getStatus();
        Map<String, Object> values = request.getValues() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.getValues());
        QuestionnaireViewDefinition.SubmissionPolicy submissionPolicy = view.getDefinition() == null
                || view.getDefinition().getSubmissionPolicy() == null
                ? defaultSubmissionPolicy()
                : view.getDefinition().getSubmissionPolicy();

        if (status == SubmissionStatus.DRAFT && (!Boolean.TRUE.equals(submissionPolicy.getDraftAllowed()) || userId == null)) {
            throw new ServiceException(TableError.SUBMISSION_NOT_ALLOWED);
        }
        if (status == SubmissionStatus.SUBMITTED) {
            LocalDateTime now = LocalDateTime.now();
            if (submissionPolicy.getStartTime() != null && now.isBefore(submissionPolicy.getStartTime())) {
                throw new ServiceException(TableError.SUBMISSION_NOT_ALLOWED);
            }
            if (submissionPolicy.getEndTime() != null && now.isAfter(submissionPolicy.getEndTime())) {
                throw new ServiceException(TableError.SUBMISSION_NOT_ALLOWED);
            }
            if (userId == null && !Boolean.TRUE.equals(submissionPolicy.getAnonymousAllowed())) {
                throw new ServiceException(TableError.SUBMISSION_NOT_ALLOWED);
            }
        }

        validateSubmissionValues(tableVersion.getColumns(), values, status);

        TableRowEntity row;
        if (userId == null) {
            row = new TableRowEntity();
        } else if (status == SubmissionStatus.DRAFT) {
            boolean hasSubmitted = tableRowRepository.countByResourceIdAndTableVersionAndUserIdAndStatus(
                    request.getResourceId(), tableVersion.getVersion(), userId, SubmissionStatus.SUBMITTED) > 0;
            if (hasSubmitted && !Boolean.TRUE.equals(submissionPolicy.getEditableAfterSubmit())) {
                throw new ServiceException(TableError.SUBMISSION_NOT_ALLOWED);
            }
            row = tableRowRepository.findFirstByResourceIdAndUserIdAndTableVersionAndStatusOrderByUpdateTimeDesc(
                            request.getResourceId(), userId, tableVersion.getVersion(), SubmissionStatus.DRAFT)
                    .orElseGet(TableRowEntity::new);
        } else {
            TableRowEntity draftRow = tableRowRepository.findFirstByResourceIdAndUserIdAndTableVersionAndStatusOrderByUpdateTimeDesc(
                    request.getResourceId(), userId, tableVersion.getVersion(), SubmissionStatus.DRAFT).orElse(null);
            TableRowEntity submittedRow = tableRowRepository.findFirstByResourceIdAndUserIdAndTableVersionAndStatusOrderByUpdateTimeDesc(
                    request.getResourceId(), userId, tableVersion.getVersion(), SubmissionStatus.SUBMITTED).orElse(null);
            if (submittedRow != null && Boolean.TRUE.equals(submissionPolicy.getEditableAfterSubmit())) {
                if (draftRow != null) {
                    tableRowRepository.delete(draftRow);
                }
                row = submittedRow;
            } else {
                if (submissionPolicy.getMaxSubmissionsPerUser() != null) {
                    long submittedCount = tableRowRepository.countByResourceIdAndTableVersionAndUserIdAndStatus(
                            request.getResourceId(), tableVersion.getVersion(), userId, SubmissionStatus.SUBMITTED);
                    if (submittedCount >= submissionPolicy.getMaxSubmissionsPerUser()) {
                        throw new ServiceException(TableError.SUBMISSION_NOT_ALLOWED);
                    }
                }
                row = draftRow == null ? new TableRowEntity() : draftRow;
            }
        }

        row.setResourceId(request.getResourceId());
        row.setTableVersion(tableVersion.getVersion());
        row.setUserId(userId);
        row.setStatus(status);
        row.setValues(values);
        row.setSubmitTime(status == SubmissionStatus.SUBMITTED ? LocalDateTime.now() : null);
        TableRowEntity saved = tableRowRepository.save(row);

        return QuestionnaireSubmissionResponse.builder()
                .submissionId(saved.getId())
                .resourceId(saved.getResourceId())
                .submittedTableVersion(saved.getTableVersion())
                .projectedTableVersion(tableVersion.getVersion())
                .userId(saved.getUserId())
                .status(saved.getStatus())
                .values(projectValues(saved.getValues(), tableVersion.getColumns()))
                .createTime(saved.getCreateTime())
                .updateTime(saved.getUpdateTime())
                .submitTime(saved.getSubmitTime())
                .build();
    }

    public PageR<QuestionnaireSubmissionResponse> listMySubmissions(QuestionnaireSubmissionListRequest request, Long userId) {
        TableEntity table = tableRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        Integer projectionVersionNumber = request.getVersion() == null ? table.getVersion() : request.getVersion();
        TableVersionEntity projectionVersion = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), projectionVersionNumber)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));
        if (projectionVersion.getStatus() != TableVersionStatus.PUBLISHED) {
            throw new ServiceException(TableError.TABLE_VERSION_STATUS_INVALID);
        }

        Page<TableRowEntity> page = tableRowRepository.findByResourceIdAndUserId(
                request.getResourceId(), userId, pageRequest(request.getPage(), request.getSize()));
        PageR<QuestionnaireSubmissionResponse> response = new PageR<>(page.getTotalElements(), page.getNumber() + 1, page.getSize());
        response.addAll(page.getContent().stream()
                .map(row -> QuestionnaireSubmissionResponse.builder()
                        .submissionId(row.getId())
                        .resourceId(row.getResourceId())
                        .submittedTableVersion(row.getTableVersion())
                        .projectedTableVersion(projectionVersion.getVersion())
                        .userId(row.getUserId())
                        .status(row.getStatus())
                        .values(projectValues(row.getValues(), projectionVersion.getColumns()))
                        .createTime(row.getCreateTime())
                        .updateTime(row.getUpdateTime())
                        .submitTime(row.getSubmitTime())
                        .build())
                .toList());
        return response;
    }

    public PageR<QuestionnaireSubmissionResponse> listSubmissions(QuestionnaireSubmissionListRequest request) {
        TableEntity table = tableRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ServiceException(TableError.TABLE_NOT_FOUND));
        Integer projectionVersionNumber = request.getVersion() == null ? table.getVersion() : request.getVersion();
        TableVersionEntity projectionVersion = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), projectionVersionNumber)
                .orElseThrow(() -> new ServiceException(TableError.TABLE_VERSION_NOT_FOUND));

        Page<TableRowEntity> page = tableRowRepository.findByResourceId(
                request.getResourceId(), pageRequest(request.getPage(), request.getSize()));
        PageR<QuestionnaireSubmissionResponse> response = new PageR<>(page.getTotalElements(), page.getNumber() + 1, page.getSize());
        response.addAll(page.getContent().stream()
                .map(row -> QuestionnaireSubmissionResponse.builder()
                        .submissionId(row.getId())
                        .resourceId(row.getResourceId())
                        .submittedTableVersion(row.getTableVersion())
                        .projectedTableVersion(projectionVersion.getVersion())
                        .userId(row.getUserId())
                        .status(row.getStatus())
                        .values(projectValues(row.getValues(), projectionVersion.getColumns()))
                        .createTime(row.getCreateTime())
                        .updateTime(row.getUpdateTime())
                        .submitTime(row.getSubmitTime())
                        .build())
                .toList());
        return response;
    }

    public boolean isDraftVersion(String resourceId, Integer version) {
        if (version == null) {
            return false;
        }
        return tableVersionRepository.findByResourceIdAndVersion(resourceId, version)
                .map(item -> item.getStatus() == TableVersionStatus.DRAFT)
                .orElse(false);
    }

    private void validateTableDefinition(List<TableColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new ServiceException(TableError.TABLE_COLUMN_INVALID);
        }
        Set<String> columnIds = new HashSet<>();
        for (TableColumn column : columns) {
            if (column == null || !StringUtils.hasText(column.getColumnId()) || !StringUtils.hasText(column.getName()) || column.getType() == null) {
                throw new ServiceException(TableError.TABLE_COLUMN_INVALID);
            }
            if (!columnIds.add(column.getColumnId())) {
                throw new ServiceException(TableError.TABLE_COLUMN_DUPLICATED);
            }
            try {
                column.validateDefinition();
                if (column.getDefaultValue() != null) {
                    column.validateValue(column.getDefaultValue());
                }
            } catch (IllegalArgumentException e) {
                throw new ServiceException(TableError.TABLE_COLUMN_INVALID, e.getMessage());
            }
        }
    }

    private void validateQuestionnaireView(QuestionnaireViewDefinition definition, List<TableColumn> columns) {
        if (definition == null || !StringUtils.hasText(definition.getTitle()) || definition.getPages() == null || definition.getPages().isEmpty()) {
            throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_INVALID);
        }
        Set<String> columnIds = columns.stream().map(TableColumn::getColumnId).collect(Collectors.toSet());
        Set<String> referencedColumnIds = new HashSet<>();
        for (QuestionnaireViewDefinition.QuestionnairePageDefinition page : definition.getPages()) {
            if (page == null || page.getPageNumber() == null || page.getPageNumber() < 1 || page.getItems() == null || page.getItems().isEmpty()) {
                throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_INVALID);
            }
            for (QuestionnaireColumnItem item : page.getItems()) {
                if (item == null || !StringUtils.hasText(item.getColumnId())) {
                    throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_INVALID);
                }
                if (!columnIds.contains(item.getColumnId())) {
                    throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_COLUMN_NOT_FOUND);
                }
                if (!referencedColumnIds.add(item.getColumnId())) {
                    throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_INVALID, "columnId duplicated in questionnaire view");
                }
            }
        }

        QuestionnaireViewDefinition.SubmissionPolicy policy = definition.getSubmissionPolicy();
        if (policy != null && policy.getStartTime() != null && policy.getEndTime() != null
                && policy.getStartTime().isAfter(policy.getEndTime())) {
            throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_INVALID, "submission time range invalid");
        }
        if (policy != null && policy.getMaxSubmissionsPerUser() != null && policy.getMaxSubmissionsPerUser() < 0) {
            throw new ServiceException(TableError.QUESTIONNAIRE_VIEW_INVALID, "maxSubmissionsPerUser must be greater than or equal to 0");
        }
    }

    private void validateSubmissionValues(List<TableColumn> columns, Map<String, Object> values, SubmissionStatus status) {
        Map<String, TableColumn> columnMap = columns.stream()
                .collect(Collectors.toMap(TableColumn::getColumnId, Function.identity()));
        for (String columnId : values.keySet()) {
            if (!columnMap.containsKey(columnId)) {
                throw new ServiceException(TableError.SUBMISSION_VALUE_INVALID);
            }
        }
        try {
            if (status == SubmissionStatus.SUBMITTED) {
                for (TableColumn column : columns) {
                    column.validateValue(values.get(column.getColumnId()));
                }
                return;
            }
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                if (isEmptyValue(entry.getValue())) {
                    continue;
                }
                columnMap.get(entry.getKey()).validateValue(entry.getValue());
            }
        } catch (IllegalArgumentException e) {
            throw new ServiceException(TableError.SUBMISSION_VALUE_INVALID, e.getMessage());
        }
    }

    private Map<String, Object> projectValues(Map<String, Object> sourceValues, List<TableColumn> columns) {
        Map<String, Object> values = sourceValues == null ? Map.of() : sourceValues;
        Map<String, Object> projected = new LinkedHashMap<>();
        for (TableColumn column : columns) {
            projected.put(column.getColumnId(), values.get(column.getColumnId()));
        }
        return projected;
    }

    private Pageable pageRequest(Integer page, Integer size) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedSize = size == null || size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "updateTime"));
    }

    private QuestionnaireViewDefinition.SubmissionPolicy defaultSubmissionPolicy() {
        return QuestionnaireViewDefinition.SubmissionPolicy.builder()
                .anonymousAllowed(false)
                .maxSubmissionsPerUser(1)
                .draftAllowed(true)
                .editableAfterSubmit(false)
                .build();
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof CharSequence text) {
            return text.toString().trim().isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
