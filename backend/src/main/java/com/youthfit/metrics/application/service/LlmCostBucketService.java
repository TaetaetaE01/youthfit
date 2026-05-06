package com.youthfit.metrics.application.service;

import com.youthfit.metrics.application.event.LlmCallRecorded;
import com.youthfit.metrics.domain.model.LlmModelPricing;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LlmCostBucketService {

    private final LlmCostBucketRepository repository;

    @Transactional
    public void recordCall(LlmCallRecorded event) {
        Instant bucketAt = event.calledAt().truncatedTo(ChronoUnit.HOURS);
        long prompt = event.promptTokens();
        long completion = event.completionTokens();
        BigDecimal cost = LlmModelPricing.of(event.model())
                .calculate(prompt, completion);
        repository.upsert(
                bucketAt,
                event.module().name(),
                event.model(),
                prompt,
                completion,
                prompt + completion,
                cost,
                Instant.now()
        );
    }
}
