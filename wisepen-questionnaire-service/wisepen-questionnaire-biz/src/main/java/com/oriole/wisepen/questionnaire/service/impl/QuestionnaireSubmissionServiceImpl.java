package com.oriole.wisepen.questionnaire.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.domain.PageR;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireSubmissionListRequest;
import com.oriole.wisepen.questionnaire.api.domain.base.QuestionnaireDefinitionBase;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionDetailResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionItemResponse;
import com.oriole.wisepen.questionnaire.api.domain.dto.res.QuestionnaireSubmissionResponse;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireSubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.domain.model.column.TableColumn;
import com.oriole.wisepen.questionnaire.api.validation.QuestionnaireSubmissionQueryValidator;
import com.oriole.wisepen.questionnaire.domain.entity.TableEntity;
import com.oriole.wisepen.questionnaire.domain.entity.TableRowEntity;
import com.oriole.wisepen.questionnaire.domain.entity.TableVersionEntity;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import com.oriole.wisepen.questionnaire.repository.CustomTableRowRepository;
import com.oriole.wisepen.questionnaire.repository.TableRepository;
import com.oriole.wisepen.questionnaire.repository.TableRowRepository;
import com.oriole.wisepen.questionnaire.repository.TableVersionRepository;
import com.oriole.wisepen.questionnaire.service.IQuestionnaireSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuestionnaireSubmissionServiceImpl implements IQuestionnaireSubmissionService {
    private final TableRepository tableRepository;
    private final TableRowRepository tableRowRepository;
    private final TableVersionRepository tableVersionRepository;
    private final CustomTableRowRepository customTableRowRepository;
    private final QuestionnaireSubmissionQueryValidator queryValidator;

    @Override
    public QuestionnaireDefinitionBase getQuestionnaireForFilling(String resourceId, Long userId) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(resourceId, table.getVersion())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));

        QuestionnaireSubmissionPolicy submissionPolicy = tableVersion.getSubmissionPolicy();
        LocalDateTime now = LocalDateTime.now();
        if (submissionPolicy.getStartTime() != null && now.isBefore(submissionPolicy.getStartTime())) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_NOT_STARTED);
        }
        if (submissionPolicy.getEndTime() != null && now.isAfter(submissionPolicy.getEndTime())) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_ENDED);
        }
        Integer maxSubmissionsPerUser = submissionPolicy.getMaxSubmissionsPerUser();
        if (maxSubmissionsPerUser != null && tableRowRepository.countByResourceIdAndUserId(resourceId, userId) >= maxSubmissionsPerUser) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_LIMIT_REACHED);
        }

        return BeanUtil.copyProperties(tableVersion, QuestionnaireDefinitionBase.class);
    }

    @Transactional
    @Override
    public String submitQuestionnaire(String resourceId, Map<String, Object> values, Long userId) {
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
        TableVersionEntity definition = tableVersionRepository.findByResourceIdAndVersion(resourceId, table.getVersion())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));
        QuestionnaireSubmissionPolicy submissionPolicy = definition.getSubmissionPolicy();
        LocalDateTime now = LocalDateTime.now();
        if (submissionPolicy.getEndTime() != null && now.isAfter(submissionPolicy.getEndTime())) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_ENDED);
        }
        Integer maxSubmissionsPerUser = submissionPolicy.getMaxSubmissionsPerUser();
        if (maxSubmissionsPerUser != null && tableRowRepository.countByResourceIdAndUserId(resourceId, userId) >= maxSubmissionsPerUser) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_LIMIT_REACHED);
        }

        // 答案以最新发布版本的列定义为准，不依赖问卷页面编排。
        List<TableColumn> columns = definition.getColumns();
        // 过滤前端传入但当前版本已不存在的题目答案，只保留能够映射到当前题目的键值对。
        Map<String, Object> submittedValues = mapAnswersToTargetVersion(values, columns);
        try {
            // 遍历当前版本的全部题目进行校验；未提交的题目取值为 null，由题目自身判断是否违反必填规则。
            for (TableColumn column : columns) {
                column.validateValue(submittedValues.get(column.getColumnId()));
            }
        } catch (IllegalArgumentException e) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_VALUE_INVALID, e.getMessage());
        }

        TableRowEntity submission = TableRowEntity.builder()
                .resourceId(resourceId)
                .userId(userId)
                .values(submittedValues)
                .build();
        TableRowEntity saved = tableRowRepository.save(submission);
        return saved.getId();
    }

    @Override
    public List<QuestionnaireSubmissionItemResponse> listMySubmissions(String resourceId, Long userId) {
        return tableRowRepository.findByResourceIdAndUserIdOrderBySubmitTimeDesc(resourceId, userId).stream()
                .map(row -> QuestionnaireSubmissionItemResponse.builder()
                        .submissionId(row.getId())
                        .updateTime(row.getUpdateTime())
                        .submitTime(row.getSubmitTime())
                        .build())
                .toList();
    }

    @Override
    public QuestionnaireSubmissionDetailResponse getSubmission(String resourceId, String submissionId, Long userId) {
        TableRowEntity submission = tableRowRepository.findByIdAndResourceIdAndUserId(submissionId, resourceId, userId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.SUBMISSION_NOT_FOUND));

        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
        TableVersionEntity definition = tableVersionRepository.findByResourceIdAndVersion(resourceId, table.getVersion())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));

        List<TableColumn> columns = definition.getColumns();

        // 答卷不绑定问卷版本，读取时按最新发布结构映射，不修改存储的答案。
        QuestionnaireSubmissionDetailResponse response = BeanUtil.copyProperties(definition, QuestionnaireSubmissionDetailResponse.class);
        response.setSubmissionId(submission.getId());
        response.setValues(mapAnswersToTargetVersion(submission.getValues(), columns));
        return response;
    }

    @Override
    public void updateSubmission(String resourceId, String submissionId, Map<String, Object> values, Long userId) {
        tableRowRepository.findByIdAndResourceIdAndUserId(submissionId, resourceId, userId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.SUBMISSION_NOT_FOUND));
        TableEntity table = tableRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));
        TableVersionEntity definition = tableVersionRepository.findByResourceIdAndVersion(resourceId, table.getVersion())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));
        QuestionnaireSubmissionPolicy submissionPolicy = definition.getSubmissionPolicy();

        LocalDateTime now = LocalDateTime.now();
        if (submissionPolicy.getEndTime() != null && now.isAfter(submissionPolicy.getEndTime())) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_ENDED);
        }
        if (!Boolean.TRUE.equals(submissionPolicy.getEditableAfterSubmit())) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_UPDATE_NOT_ALLOWED);
        }

        // 按最新发布版本建立题目索引，用于限定本次可修改的答案并取得对应题型的校验规则。
        Map<String, TableColumn> columnsById = new LinkedHashMap<>();
        definition.getColumns()
                .forEach(column -> columnsById.put(column.getColumnId(), column));

        // 本接口执行局部更新：只处理请求中命中当前题目的答案，未传入的答案和历史版本答案保持不变。
        Map<String, Object> changedValues = new LinkedHashMap<>();
        if (values != null) {
            try {
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    TableColumn column = columnsById.get(entry.getKey());
                    if (column == null) {
                        // 当前版本不存在该题目，忽略本次更新；数据库中同 columnId 的历史答案保持不变。
                        continue;
                    }
                    // 有效变更按当前题型校验；数据库中已有的答案会被覆盖，不存在的答案会被新增。
                    column.validateValue(entry.getValue());
                    changedValues.put(entry.getKey(), entry.getValue());
                }
            } catch (IllegalArgumentException e) {
                throw new ServiceException(QuestionnaireError.SUBMISSION_VALUE_INVALID, e.getMessage());
            }
        }

        // Repository 使用 MongoDB $set 写入有效变更，不替换整个 values，也不清理历史答案。
        customTableRowRepository.updateValues(resourceId, submissionId, userId, changedValues);
    }

    @Override
    public PageR<QuestionnaireSubmissionResponse> listAllSubmissions(QuestionnaireSubmissionListRequest request) {
        TableEntity table = tableRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_NOT_FOUND));

        TableVersionEntity tableVersion = tableVersionRepository.findByResourceIdAndVersion(request.getResourceId(), table.getVersion())
                .orElseThrow(() -> new ServiceException(QuestionnaireError.TABLE_VERSION_NOT_FOUND));
        boolean anonymous = Boolean.TRUE.equals(tableVersion.getSubmissionPolicy().getAnonymous());
        if (anonymous && request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_QUERY_INVALID, "匿名问卷不支持按 userIds 筛选");
        }

        // 建立 columnId 索引，后续用当前问卷题型解析筛选和排序条件。
        Map<String, TableColumn> columnsById = new LinkedHashMap<>();
        tableVersion.getColumns()
                .forEach(column -> columnsById.put(column.getColumnId(), column));

        try {
            queryValidator.validateFilterGroup(request.getFilters(), columnsById);
            queryValidator.validateSort(request.getSorts(), columnsById);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(QuestionnaireError.SUBMISSION_QUERY_INVALID, e.getMessage());
        }

        // Repository 负责将已校验的条件编译为 Mongo 查询；未指定排序时按提交时间倒序。
        Page<TableRowEntity> submissionPage = customTableRowRepository.listSubmissions(
                request.getResourceId(), request.getUserIds(), request.getFilters(), request.getSorts(),
                request.getPage() - 1, request.getSize());

        // 返回当前页的完整答案；匿名问卷只隐藏 userId，不修改答案内容。
        PageR<QuestionnaireSubmissionResponse> response = new PageR<>(
                submissionPage.getTotalElements(), submissionPage.getNumber() + 1, submissionPage.getSize());
        response.addAll(submissionPage.getContent().stream()
                .map(row -> QuestionnaireSubmissionResponse.builder()
                        .submissionId(row.getId())
                        .userId(anonymous ? null : row.getUserId())
                        .values(row.getValues())
                        .submitTime(row.getSubmitTime())
                        .updateTime(row.getUpdateTime())
                        .build())
                .toList());
        return response;
    }

    @Transactional
    @Override
    public void deleteSubmissions(String resourceId, String submissionId) {
        if (submissionId != null) {
            tableRowRepository.deleteByIdAndResourceId(submissionId, resourceId);
            log.info("questionnaire submission deleted. resourceId={} submissionId={}", resourceId, submissionId);
            return;
        }
        long deletedCount = tableRowRepository.deleteByResourceId(resourceId);
        log.info("questionnaire submissions deleted. resourceId={} count={}", resourceId, deletedCount);
    }

    /**
     * 按目标问卷版本的题目列表投影答案，仅构造新的答案 Map，不修改原始答案或数据库数据。
     * 数据库可以保留已删除题目的历史答案，调用方通过传入不同版本的题目列表获取对应版本的答案。
     */
    private Map<String, Object> mapAnswersToTargetVersion(Map<String, Object> sourceValues, List<TableColumn> columns) {
        Map<String, Object> values = sourceValues == null ? Map.of() : sourceValues;
        Map<String, Object> projected = new LinkedHashMap<>();
        for (TableColumn column : columns) {
            String columnId = column.getColumnId();
            if (values.containsKey(columnId)) {
                projected.put(columnId, values.get(columnId));
            }
        }
        return projected;
    }

}
