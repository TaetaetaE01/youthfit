package com.youthfit.metrics.application.service;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LlmCostBucketServiceTest {

    @Test
    void recordCall_은_calledAt_을_시간단위로_truncate_해서_upsert_한다() {
        LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
        LlmCostBucketService service = new LlmCostBucketService(repo);

        Instant calledAt = Instant.parse("2026-05-06T10:42:17Z");
        LlmCallRecorded event = new LlmCallRecorded(LlmModule.QNA, "gpt-4o-mini", 1000, 500, calledAt);

        service.recordCall(event);

        ArgumentCaptor<Instant> bucketAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).upsert(
                bucketAtCaptor.capture(),
                eq("QNA"),
                eq("gpt-4o-mini"),
                eq(1000L),
                eq(500L),
                eq(1500L),
                any(BigDecimal.class),
                any(Instant.class)
        );
        assertThat(bucketAtCaptor.getValue()).isEqualTo(Instant.parse("2026-05-06T10:00:00Z"));
    }

    @Test
    void recordCall_은_미등록_모델도_cost_0_으로_upsert_를_시도한다() {
        LlmCostBucketRepository repo = mock(LlmCostBucketRepository.class);
        LlmCostBucketService service = new LlmCostBucketService(repo);

        LlmCallRecorded event = new LlmCallRecorded(
                LlmModule.GUIDE, "unknown-model-x", 100, 50, Instant.now()
        );

        service.recordCall(event);

        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repo).upsert(any(), any(), any(), anyLong(), anyLong(), anyLong(),
                            costCaptor.capture(), any());
        assertThat(costCaptor.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
