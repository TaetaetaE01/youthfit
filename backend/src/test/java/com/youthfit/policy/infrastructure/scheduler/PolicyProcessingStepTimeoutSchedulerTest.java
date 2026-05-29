package com.youthfit.policy.infrastructure.scheduler;

import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("PolicyProcessingStepTimeoutScheduler")
@ExtendWith(MockitoExtension.class)
class PolicyProcessingStepTimeoutSchedulerTest {

    @Mock private PolicyProcessingStepRepository repository;

    @Test
    @DisplayName("stale 행이 있으면 markTimedOut 호출 — JPA dirty checking 의존, save 호출 없음")
    void expireStaleSteps_marksAndSavesEachStale() {
        // given — fixed clock: 2026-05-29T10:00:00Z, TIMEOUT 10분 → threshold = 09:50:00Z
        Instant now = Instant.parse("2026-05-29T10:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        PolicyProcessingStepTimeoutScheduler scheduler =
                new PolicyProcessingStepTimeoutScheduler(repository, fixedClock);

        PolicyProcessingStep stale1 = PolicyProcessingStep.start(1L, ProcessingStep.GUIDE, 1);
        PolicyProcessingStep stale2 = PolicyProcessingStep.start(2L, ProcessingStep.RAG_INDEXING, 1);
        Instant expectedThreshold = now.minusSeconds(600);
        given(repository.findActiveStaleBefore(expectedThreshold)).willReturn(List.of(stale1, stale2));

        // when
        scheduler.expireStaleSteps();

        // then — managed entity dirty checking, no save call expected
        assertThat(stale1.getStatus().name()).isEqualTo("FAILED");
        assertThat(stale1.getReason()).isEqualTo("timeout");
        assertThat(stale1.getFinishedAt()).isNotNull();
        assertThat(stale2.getStatus().name()).isEqualTo("FAILED");
        assertThat(stale2.getReason()).isEqualTo("timeout");
        assertThat(stale2.getFinishedAt()).isNotNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("stale 행이 없으면 save 호출 없음")
    void expireStaleSteps_emptyList_noop() {
        // given
        Instant now = Instant.parse("2026-05-29T10:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));
        PolicyProcessingStepTimeoutScheduler scheduler =
                new PolicyProcessingStepTimeoutScheduler(repository, fixedClock);

        Instant expectedThreshold = now.minusSeconds(600);
        given(repository.findActiveStaleBefore(expectedThreshold)).willReturn(List.of());

        // when
        scheduler.expireStaleSteps();

        // then — findActiveStaleBefore 외 다른 호출 없음
        verify(repository).findActiveStaleBefore(expectedThreshold);
        verifyNoMoreInteractions(repository);
    }
}
