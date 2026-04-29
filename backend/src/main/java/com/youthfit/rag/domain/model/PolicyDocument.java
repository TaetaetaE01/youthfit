package com.youthfit.rag.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
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
        name = "policy_document",
        indexes = {
                @Index(name = "idx_policy_document_attachment", columnList = "attachment_id")
        }
)
public class PolicyDocument extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Builder
    private PolicyDocument(Long policyId,
                           int chunkIndex,
                           String content,
                           String sourceHash,
                           Long attachmentId,
                           Integer pageStart,
                           Integer pageEnd) {
        this.policyId = policyId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.sourceHash = sourceHash;
        this.attachmentId = attachmentId;
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public void updateEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public boolean hasEmbedding() {
        return this.embedding != null && this.embedding.length > 0;
    }
}
