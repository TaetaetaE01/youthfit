package com.youthfit.policy.application.service;

import com.youthfit.policy.domain.model.*;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class EnrichmentJobTimeoutSchedulerTest {

    @Test
    void 활성_5분초과_잡을_FAILED로_마킹한다() {
        EnrichmentJobRepository repo = mock(EnrichmentJobRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-21T00:10:00Z"), ZoneId.of("UTC"));
        EnrichmentJobTimeoutScheduler scheduler = new EnrichmentJobTimeoutScheduler(repo, clock);

        EnrichmentJob stale = EnrichmentJob.pending(1L, "actor",
                List.of(PolicyReferenceSite.auto("n", "https://a.example.com")),
                1, LocalDateTime.of(2026, 5, 21, 0, 4, 0));
        when(repo.findActiveStaleBefore(any())).thenReturn(List.of(stale));

        scheduler.expireStaleJobs();

        verify(repo).save(argThat(j -> j.getStatus() == EnrichmentJobStatus.FAILED
                && j.getErrorMessage() != null
                && j.getErrorMessage().contains("timeout")));
    }
}
