package com.youthfit.rag.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_document")
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

    @Builder
    private PolicyDocument(Long policyId, int chunkIndex, String content, String sourceHash) {
        this.policyId = policyId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.sourceHash = sourceHash;
    }
}
