package com.youthfit.policy.infrastructure.scheduler;

import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrichmentJobTimeoutSchedulerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-21T00:10:00Z"), ZoneId.of("UTC"));

    @Test
    void 활성_5분초과_잡을_FAILED로_마킹한다() {
        EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
        EnrichmentJobTimeoutScheduler scheduler = new EnrichmentJobTimeoutScheduler(repo, FIXED_CLOCK);

        EnrichmentJob stale = EnrichmentJob.pending(1L, "actor",
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                1, LocalDateTime.of(2026, 5, 21, 0, 4, 0));
        when(repo.findActiveStaleBefore(any())).thenReturn(List.of(stale));

        scheduler.expireStaleJobs();

        assertThat(stale.getStatus()).isEqualTo(EnrichmentJobStatus.FAILED);
        assertThat(stale.getErrorMessage()).contains("timeout");
        assertThat(stale.getFinishedAt()).isEqualTo(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneId.of("UTC")));
        verify(repo, never()).save(any());
    }

    @Test
    void stale_잡이_없으면_아무_작업도_하지_않는다() {
        EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
        EnrichmentJobTimeoutScheduler scheduler = new EnrichmentJobTimeoutScheduler(repo, FIXED_CLOCK);

        when(repo.findActiveStaleBefore(any())).thenReturn(List.of());

        scheduler.expireStaleJobs();

        verify(repo, never()).save(any());
    }

    @Test
    void 다수의_stale_잡을_일괄_FAILED로_마킹한다() {
        EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
        EnrichmentJobTimeoutScheduler scheduler = new EnrichmentJobTimeoutScheduler(repo, FIXED_CLOCK);

        EnrichmentJob stale1 = EnrichmentJob.pending(1L, "actor",
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                1, LocalDateTime.of(2026, 5, 21, 0, 4, 0));
        EnrichmentJob stale2 = EnrichmentJob.pending(2L, "actor",
                List.of(PolicyReferenceSite.auto("n", "https://b.example.com")),
                1, LocalDateTime.of(2026, 5, 21, 0, 3, 30));
        when(repo.findActiveStaleBefore(any())).thenReturn(List.of(stale1, stale2));

        scheduler.expireStaleJobs();

        assertThat(stale1.getStatus()).isEqualTo(EnrichmentJobStatus.FAILED);
        assertThat(stale1.getErrorMessage()).contains("timeout");
        assertThat(stale2.getStatus()).isEqualTo(EnrichmentJobStatus.FAILED);
        assertThat(stale2.getErrorMessage()).contains("timeout");
        verify(repo, never()).save(any());
    }
}
