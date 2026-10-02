package com.oriole.wisepen.questionnaire.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.questionnaire.api.domain.dto.req.QuestionnaireCreateRequest;
import com.oriole.wisepen.questionnaire.api.domain.model.OptionDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnaireDefinition;
import com.oriole.wisepen.questionnaire.api.domain.model.QuestionnairePage;
import com.oriole.wisepen.questionnaire.api.domain.model.SubmissionPolicy;
import com.oriole.wisepen.questionnaire.api.enums.QuestionType;
import com.oriole.wisepen.questionnaire.api.enums.QuestionnaireVersionStatus;
import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireEntity;
import com.oriole.wisepen.questionnaire.domain.entity.QuestionnaireVersionEntity;
import com.oriole.wisepen.questionnaire.exception.QuestionnaireError;
import com.oriole.wisepen.questionnaire.repository.QuestionnaireRepository;
import com.oriole.wisepen.questionnaire.repository.QuestionnaireVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionnaireServiceImplTest {

    @Mock
    private QuestionnaireRepository questionnaireRepository;

    @Mock
    private QuestionnaireVersionRepository questionnaireVersionRepository;

    @InjectMocks
    private QuestionnaireServiceImpl questionnaireService;

    @Test
    void createQuestionnaireInsertsMasterAndDraftVersion() {
        QuestionnaireCreateRequest request = createRequest("resource-1");
        when(questionnaireRepository.existsById("resource-1")).thenReturn(false);

        assertEquals("resource-1", questionnaireService.createQuestionnaire(request));

        ArgumentCaptor<QuestionnaireEntity> masterCaptor = ArgumentCaptor.forClass(QuestionnaireEntity.class);
        ArgumentCaptor<QuestionnaireVersionEntity> versionCaptor = ArgumentCaptor.forClass(QuestionnaireVersionEntity.class);
        verify(questionnaireRepository).insert(masterCaptor.capture());
        verify(questionnaireVersionRepository).insert(versionCaptor.capture());
        assertEquals("resource-1", masterCaptor.getValue().getResourceId());
        assertEquals(1, masterCaptor.getValue().getVersion());
        assertEquals(QuestionnaireVersionStatus.DRAFT, versionCaptor.getValue().getStatus());
        assertSame(request.getDefinition(), versionCaptor.getValue().getDefinition());
    }

    @Test
    void getQuestionnaireReadsVersionPointedToByMaster() {
        QuestionnaireEntity master = QuestionnaireEntity.builder().resourceId("resource-1").version(2).build();
        QuestionnaireVersionEntity version = QuestionnaireVersionEntity.builder()
                .resourceId("resource-1")
                .version(2)
                .status(QuestionnaireVersionStatus.PUBLISHED)
                .definition(createRequest("resource-1").getDefinition())
                .submissionPolicy(SubmissionPolicy.builder().anonymousAllowed(true).build())
                .build();
        when(questionnaireRepository.findById("resource-1")).thenReturn(Optional.of(master));
        when(questionnaireVersionRepository.findByResourceIdAndVersion("resource-1", 2))
                .thenReturn(Optional.of(version));

        var response = questionnaireService.getQuestionnaire("resource-1");

        assertEquals(2, response.getVersion());
        assertEquals(QuestionnaireVersionStatus.PUBLISHED, response.getStatus());
        verify(questionnaireVersionRepository).findByResourceIdAndVersion("resource-1", 2);
    }

    @Test
    void createQuestionnaireRejectsExistingResource() {
        when(questionnaireRepository.existsById("resource-1")).thenReturn(true);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> questionnaireService.createQuestionnaire(createRequest("resource-1")));

        assertEquals(QuestionnaireError.QUESTIONNAIRE_ALREADY_EXISTS, exception.getErrorResult());
        verify(questionnaireVersionRepository, never()).insert(any(QuestionnaireVersionEntity.class));
    }

    private QuestionnaireCreateRequest createRequest(String resourceId) {
        QuestionDefinition question = QuestionDefinition.builder()
                .questionId("question-1")
                .type(QuestionType.SINGLE_CHOICE)
                .title("是否满意")
                .options(List.of(OptionDefinition.builder().optionId("option-1").label("满意").build()))
                .build();
        QuestionnaireDefinition definition = QuestionnaireDefinition.builder()
                .title("体验调查")
                .pages(List.of(QuestionnairePage.builder().pageId("page-1").questions(List.of(question)).build()))
                .build();
        return QuestionnaireCreateRequest.builder()
                .resourceId(resourceId)
                .definition(definition)
                .submissionPolicy(SubmissionPolicy.builder().build())
                .build();
    }
}
