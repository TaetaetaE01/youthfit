package com.youthfit.policy.application.service;

import com.youthfit.policy.application.port.ForceEnrichTrigger;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnrichmentJobServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-21T00:00:00Z"), ZoneId.of("UTC"));

    private EnrichmentJobRepository jobRepo;
    private PolicyRepository policyRepo;
    private ForceEnrichTrigger trigger;
    private EnrichmentJobService service;

    @BeforeEach
    void setUp() {
        jobRepo = mock(EnrichmentJobRepository.class);
        policyRepo = mock(PolicyRepository.class);
        trigger = mock(ForceEnrichTrigger.class);
        service = new EnrichmentJobService(jobRepo, policyRepo, trigger, FIXED_CLOCK);
    }

    @Test
    void create_은_새_PENDING_잡을_저장하고_트리거를_호출한다() {
        Policy policy = mock(Policy.class);
        when(policy.getReferenceSites()).thenReturn(
                List.of(PolicyReferenceSite.auto("기존", "https://a.example.com")));
        when(policyRepo.findById(42L)).thenReturn(Optional.of(policy));
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(Optional.empty());
        when(jobRepo.maxAttemptByPolicyId(42L)).thenReturn(0);
        when(jobRepo.countRecentByPolicyId(eq(42L), any())).thenReturn(0L);
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EnrichmentJob created = service.create(42L, "admin@youthfit", null);

        assertThat(created.getStatus()).isEqualTo(EnrichmentJobStatus.PENDING);
        assertThat(created.getAttempt()).isEqualTo(1);
        verify(trigger).forceEnrich(any(), eq(42L),
                argThat(urls -> urls.size() == 1
                        && urls.get(0).url().equals("https://a.example.com")));
    }

    @Test
    void 진행중_잡이_있으면_409_예외() {
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(
                Optional.of(EnrichmentJob.pending(42L, "x",
                        List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                        1, LocalDateTime.now(FIXED_CLOCK))));

        assertThatThrownBy(() -> service.create(42L, "admin@youthfit", null))
                .isInstanceOf(EnrichmentJobConflictException.class);

        verifyNoInteractions(trigger);
    }

    @Test
    void 빈_referenceSites_는_거절() {
        Policy policy = mock(Policy.class);
        when(policy.getReferenceSites()).thenReturn(List.of());
        when(policyRepo.findById(42L)).thenReturn(Optional.of(policy));
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(Optional.empty());
        when(jobRepo.countRecentByPolicyId(eq(42L), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(42L, "admin@youthfit", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("referenceSites");
    }

    @Test
    void complete_SUCCESS_는_status를_SUCCESS로_옮긴다() {
        EnrichmentJob job = EnrichmentJob.pending(42L, "x",
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                1, LocalDateTime.now(FIXED_CLOCK));
        when(jobRepo.findById(100L)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.complete(100L, EnrichmentJobStatus.SUCCESS, null);

        assertThat(job.getStatus()).isEqualTo(EnrichmentJobStatus.SUCCESS);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void complete_이미_종결된_잡_재콜백은_무시() {
        EnrichmentJob job = EnrichmentJob.pending(42L, "x",
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                1, LocalDateTime.now(FIXED_CLOCK));
        job.markSuccess(LocalDateTime.now(FIXED_CLOCK));
        when(jobRepo.findById(100L)).thenReturn(Optional.of(job));

        service.complete(100L, EnrichmentJobStatus.FAILED, "redundant");

        assertThat(job.getStatus()).isEqualTo(EnrichmentJobStatus.SUCCESS);
    }

    @Test
    void trigger_실패시_잡은_FAILED_n8n_unreachable로_저장된다() {
        Policy policy = mock(Policy.class);
        when(policy.getReferenceSites()).thenReturn(
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")));
        when(policyRepo.findById(42L)).thenReturn(Optional.of(policy));
        when(jobRepo.findActiveByPolicyId(42L)).thenReturn(Optional.empty());
        when(jobRepo.maxAttemptByPolicyId(42L)).thenReturn(0);
        when(jobRepo.countRecentByPolicyId(eq(42L), any())).thenReturn(0L);
        when(jobRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new ForceEnrichTrigger.EnrichmentTriggerException("connect timeout", null))
                .when(trigger).forceEnrich(any(), eq(42L), anyList());

        EnrichmentJob created = service.create(42L, "admin@youthfit", null);

        assertThat(created.getStatus()).isEqualTo(EnrichmentJobStatus.FAILED);
        assertThat(created.getErrorMessage()).contains("n8n_unreachable");
    }
}
