package com.youthfit.qna.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "qna_question_cache",
        indexes = {
                @Index(name = "idx_qna_question_cache_policy", columnList = "policy_id")
        }
)
public class QnaQuestionCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sources_json", nullable = false, columnDefinition = "JSONB")
    private String sourcesJson;

    @Builder
    private QnaQuestionCache(Long policyId,
                             String questionText,
                             float[] embedding,
                             String answer,
                             String sourcesJson) {
        this.policyId = policyId;
        this.questionText = questionText;
        this.embedding = embedding;
        this.answer = answer;
        this.sourcesJson = sourcesJson;
    }
}
