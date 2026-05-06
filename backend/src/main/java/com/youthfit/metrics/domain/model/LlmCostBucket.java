package com.youthfit.metrics.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Table(
    name = "llm_cost_bucket",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_llm_cost_bucket_at_module_model",
        columnNames = {"bucket_at", "module", "model"}
    ),
    indexes = {
        @Index(name = "idx_llm_cost_bucket_at", columnList = "bucket_at DESC"),
        @Index(name = "idx_llm_cost_bucket_module_at", columnList = "module, bucket_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCostBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bucket_at", nullable = false)
    private Instant bucketAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 20)
    private LlmModule module;

    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "estimated_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static LlmCostBucket initial(Instant bucketAt, LlmModule module, String model,
                                        long promptTokens, long completionTokens, BigDecimal cost,
                                        Instant now) {
        LlmCostBucket b = new LlmCostBucket();
        b.bucketAt = bucketAt;
        b.module = module;
        b.model = model;
        b.callCount = 1;
        b.promptTokens = promptTokens;
        b.completionTokens = completionTokens;
        b.totalTokens = promptTokens + completionTokens;
        b.estimatedCostUsd = cost;
        b.createdAt = now;
        b.updatedAt = now;
        return b;
    }
}
