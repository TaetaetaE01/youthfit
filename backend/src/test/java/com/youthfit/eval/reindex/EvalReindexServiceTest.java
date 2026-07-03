package com.youthfit.eval.reindex;

import com.youthfit.ingestion.application.service.AttachmentReindexService;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.domain.model.PolicyDocument;
import com.youthfit.rag.domain.repository.PolicyDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@DisplayName("EvalReindexService")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvalReindexServiceTest {

    @InjectMocks
    private EvalReindexService service;

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyDocumentRepository policyDocumentRepository;
    @Mock private AttachmentReindexService attachmentReindexService;

    @Test
    @DisplayName("reindexPolicy 는 삭제 → 무이벤트 재인덱싱 순서로 실행한다")
    void reindexPolicy_deletesThenReindexes() {
        given(attachmentReindexService.reindexWithoutEvents(1L))
                .willReturn(new IndexingResult(1L, 5, true));

        boolean result = service.reindexPolicy(1L);

        assertThat(result).isTrue();
        InOrder order = inOrder(policyDocumentRepository, attachmentReindexService);
        order.verify(policyDocumentRepository).deleteByPolicyId(1L);
        order.verify(attachmentReindexService).reindexWithoutEvents(1L);
    }

    @Test
    @DisplayName("reindexWithoutEvents 가 null(스킵)이면 예외를 던져 롤백을 유도한다")
    void reindexPolicy_throwsOnSkipToTriggerRollback() {
        given(attachmentReindexService.reindexWithoutEvents(1L)).willReturn(null);

        assertThatThrownBy(() -> service.reindexPolicy(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("reindexWithoutEvents 결과가 청크 0건이면 예외를 던져 롤백을 유도한다")
    void reindexPolicy_throwsOnEmptyResultToTriggerRollback() {
        given(attachmentReindexService.reindexWithoutEvents(1L))
                .willReturn(new IndexingResult(1L, 0, true));

        assertThatThrownBy(() -> service.reindexPolicy(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("findTargets: policyIds 미지정이면 청크 보유 전 정책")
    void findTargets_allPoliciesWithChunks() {
        Policy withChunks = mock(Policy.class);
        given(withChunks.getId()).willReturn(1L);
        Policy withoutChunks = mock(Policy.class);
        given(withoutChunks.getId()).willReturn(2L);
        List<Policy> all = List.of(withChunks, withoutChunks);
        given(policyRepository.findAllForStats()).willReturn(all);
        List<PolicyDocument> chunks = List.of(mock(PolicyDocument.class));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(1L)).willReturn(chunks);
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(2L)).willReturn(List.of());

        List<Policy> targets = service.findTargets(null);

        assertThat(targets).containsExactly(withChunks);
    }

    @Test
    @DisplayName("findTargets: policyIds 지정 시 해당 정책만 (청크 보유 필터 동일)")
    void findTargets_specificIds() {
        Policy p = mock(Policy.class);
        given(p.getId()).willReturn(7L);
        List<Policy> found = List.of(p);
        given(policyRepository.findAllById(List.of(7L))).willReturn(found);
        List<PolicyDocument> chunks = List.of(mock(PolicyDocument.class));
        given(policyDocumentRepository.findByPolicyIdOrderByChunkIndex(7L)).willReturn(chunks);

        List<Policy> targets = service.findTargets(List.of(7L));

        assertThat(targets).containsExactly(p);
    }
}
