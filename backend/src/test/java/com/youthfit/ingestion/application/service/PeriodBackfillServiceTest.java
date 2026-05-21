package com.youthfit.ingestion.application.service;

import com.youthfit.common.domain.PeriodSource;
import com.youthfit.common.event.PolicyAttachmentReindexedEvent;
import com.youthfit.common.event.PolicyPeriodUpdated;
import com.youthfit.ingestion.domain.model.ResolvedPeriod;
import com.youthfit.ingestion.domain.service.PeriodResolver;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.repository.PolicyAttachmentRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("PeriodBackfillService")
class PeriodBackfillServiceTest {

    private final PolicyRepository policyRepository = mock(PolicyRepository.class);
    private final PolicyAttachmentRepository attachmentRepository = mock(PolicyAttachmentRepository.class);
    private final PeriodResolver resolver = mock(PeriodResolver.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private final PeriodBackfillService service =
            new PeriodBackfillService(policyRepository, attachmentRepository, resolver, publisher);

    @Test
    @DisplayName("기존 confidence ≥ 0.70 이면 보강 안 함")
    void skipsWhenHighConfidence() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(0.85);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verifyNoInteractions(resolver);
        verify(publisher, never()).publishEvent(any(PolicyPeriodUpdated.class));
    }

    @Test
    @DisplayName("기존 NULL + 새 결과 > 0.55 → 업데이트 + 이벤트 발행")
    void updatesWhenBetter() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(null);
        when(p.getId()).thenReturn(1L);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        PolicyAttachment attachment = attachmentWithText("신청기간 2026.3.1 ~ 2026.4.30");
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of(attachment));
        when(resolver.resolve(any())).thenReturn(new ResolvedPeriod(
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
                PeriodSource.ATTACHMENT_LABELED, 0.75, "..."));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p).updateApplyPeriod(
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
                PeriodSource.ATTACHMENT_LABELED, 0.75, "...");
        verify(publisher).publishEvent(any(PolicyPeriodUpdated.class));
    }

    @Test
    @DisplayName("새 confidence 가 기존 + 0.05 마진을 못 넘으면 업데이트 안 함")
    void skipsBelowMargin() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(0.60);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(resolver.resolve(any())).thenReturn(new ResolvedPeriod(
                LocalDate.of(2026,3,1), LocalDate.of(2026,4,30),
                PeriodSource.ATTACHMENT_LABELED, 0.62, "..."));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p, never()).updateApplyPeriod(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기존이 완전 범위인데 새 결과가 부분 범위(DEADLINE_ONLY 등)면 거부 — 정보 파괴 방지")
    void skipsPartialOverwriteOfFullRange() {
        Policy p = mock(Policy.class);
        when(p.getApplyStart()).thenReturn(LocalDate.of(2026, 3, 1));
        when(p.getApplyEnd()).thenReturn(LocalDate.of(2026, 4, 30));
        when(p.getApplyPeriodConfidence()).thenReturn(0.60); // n8n
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        // 새 결과: DEADLINE_ONLY (start=null) — confidence 가 높아도 부분 범위
        when(resolver.resolve(any())).thenReturn(new ResolvedPeriod(
                null, LocalDate.of(2026, 6, 30),
                PeriodSource.ATTACHMENT_LABELED, 0.85, "마감 ..."));

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p, never()).updateApplyPeriod(any(), any(), any(), any(), any());
        verify(publisher, never()).publishEvent(any(PolicyPeriodUpdated.class));
    }

    @Test
    @DisplayName("새 결과가 empty 면 덮어쓰지 않는다")
    void skipsWhenEmpty() {
        Policy p = mock(Policy.class);
        when(p.getApplyPeriodConfidence()).thenReturn(null);
        when(policyRepository.findById(1L)).thenReturn(Optional.of(p));
        when(attachmentRepository.findExtractedByPolicyId(1L)).thenReturn(List.of());
        when(resolver.resolve(any())).thenReturn(ResolvedPeriod.empty());

        service.onAttachmentsReindexed(new PolicyAttachmentReindexedEvent(1L));

        verify(p, never()).updateApplyPeriod(any(), any(), any(), any(), any());
        verifyNoInteractions(publisher);
    }

    private PolicyAttachment attachmentWithText(String text) {
        PolicyAttachment a = mock(PolicyAttachment.class);
        when(a.getExtractedText()).thenReturn(text);
        return a;
    }
}
