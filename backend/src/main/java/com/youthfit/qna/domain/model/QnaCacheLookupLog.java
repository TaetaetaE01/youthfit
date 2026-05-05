package com.youthfit.qna.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
    name = "qna_cache_lookup_log",
    indexes = {
        @Index(name = "idx_qna_cache_lookup_at",        columnList = "looked_up_at DESC"),
        @Index(name = "idx_qna_cache_lookup_result_at", columnList = "result, looked_up_at DESC"),
        @Index(name = "idx_qna_cache_lookup_policy_at", columnList = "policy_id, looked_up_at DESC")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QnaCacheLookupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "normalized_text", nullable = false, columnDefinition = "TEXT")
    private String normalizedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private LookupResultType result;

    @Column(name = "matched_cached_id")
    private Long matchedCachedId;

    @Column(name = "similarity_score", precision = 6, scale = 5)
    private BigDecimal similarityScore;

    @Column(name = "threshold_applied", nullable = false, precision = 6, scale = 5)
    private BigDecimal thresholdApplied;

    @Column(name = "llm_call_made", nullable = false)
    private boolean llmCallMade;

    @Column(name = "looked_up_at", nullable = false)
    private LocalDateTime lookedUpAt;

    public static QnaCacheLookupLog of(
            Long policyId,
            String questionText,
            String normalizedText,
            LookupResultType result,
            Long matchedCachedId,
            BigDecimal similarityScore,
            BigDecimal thresholdApplied,
            boolean llmCallMade,
            LocalDateTime lookedUpAt
    ) {
        QnaCacheLookupLog log = new QnaCacheLookupLog();
        log.policyId = policyId;
        log.questionText = questionText;
        log.normalizedText = normalizedText;
        log.result = result;
        log.matchedCachedId = matchedCachedId;
        log.similarityScore = similarityScore;
        log.thresholdApplied = thresholdApplied;
        log.llmCallMade = llmCallMade;
        log.lookedUpAt = lookedUpAt;
        return log;
    }
}
