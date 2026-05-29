package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dto.PolicyProcessingFilter;
import com.youthfit.admin.application.dto.PolicyProcessingListCommand;
import com.youthfit.admin.application.dto.PolicyProcessingListResult;
import com.youthfit.admin.application.dto.PolicyProcessingSort;
import com.youthfit.admin.domain.model.PolicyProcessingCompleteness;
import com.youthfit.policy.domain.model.AttachmentExtractionCounts;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AdminPolicyProcessingService")
@ExtendWith(MockitoExtension.class)
class AdminPolicyProcessingServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyProcessingStepRepository stepRepository;
    @Mock private PolicyAttachmentRepository attachmentRepository;
    @Mock private PolicyDocumentRepository documentRepository;

    @InjectMocks private AdminPolicyProcessingService service;

    private static final PolicyProcessingListCommand DEFAULT_COMMAND = new PolicyProcessingListCommand(
            null, null, PolicyProcessingFilter.ALL, PolicyProcessingSort.UPDATED_DESC, 0, 50);

    @Test
    @DisplayName("RAG SUCCESS + 첨부 0건이면 완성도는 COMPLETE 다")
    void completenessIsCompleteWhenRagSuccessAndNoAttachments() {
        givenSinglePolicyPage(100L, "월세 지원", "11");
        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(
                Map.of(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS))
        );
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(
                Map.of(100L, new AttachmentExtractionCounts(0, 0, 0))
        );
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of()
        );

        PolicyProcessingListResult result = service.findProcessingPolicies(DEFAULT_COMMAND);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness())
                .isEqualTo(PolicyProcessingCompleteness.COMPLETE);
    }

    @Test
    @DisplayName("RAG SUCCESS 지만 첨부 일부만 임베딩됐다면 완성도는 PARTIAL 이다")
    void completenessIsPartialWhenAttachmentsExtractedButEmbeddingMissing() {
        givenSinglePolicyPage(100L, "도전지원금", "11");
        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(
                Map.of(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.SUCCESS))
        );
        // total=5, downloaded=5, extracted=4. 일부만 추출, 임베딩 2건
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(
                Map.of(100L, new AttachmentExtractionCounts(5, 5, 4))
        );
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of(100L, 2L)
        );

        PolicyProcessingListResult result = service.findProcessingPolicies(DEFAULT_COMMAND);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness())
                .isEqualTo(PolicyProcessingCompleteness.PARTIAL);
    }

    @Test
    @DisplayName("RAG FAILED 이면 완성도는 INCOMPLETE 다")
    void completenessIsIncompleteWhenRagFailed() {
        givenSinglePolicyPage(100L, "월세대출", "11");
        given(stepRepository.findLatestStatusMapByPolicyIds(anyList())).willReturn(
                Map.of(100L, Map.of(ProcessingStep.RAG_INDEXING, ProcessingStatus.FAILED))
        );
        given(attachmentRepository.aggregateExtractionByPolicyIds(anyList())).willReturn(
                Map.of(100L, AttachmentExtractionCounts.empty())
        );
        given(documentRepository.countAttachmentEmbeddingsByPolicyIds(anyList())).willReturn(
                Map.of()
        );

        PolicyProcessingListResult result = service.findProcessingPolicies(DEFAULT_COMMAND);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).completeness())
                .isEqualTo(PolicyProcessingCompleteness.INCOMPLETE);
    }

    private void givenSinglePolicyPage(Long id, String title, String regionCode) {
        Policy policy = mock(Policy.class);
        lenient().when(policy.getId()).thenReturn(id);
        lenient().when(policy.getTitle()).thenReturn(title);
        lenient().when(policy.getRegionCode()).thenReturn(regionCode);
        Page<Policy> page = new PageImpl<>(List.of(policy));
        given(policyRepository.findForAdminProcessing(
                isNull(), isNull(), any(Sort.class), any(Pageable.class)))
                .willReturn(page);
    }
}
