package com.youthfit.guide.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guide")
public class Guide extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false, unique = true)
    private Long policyId;

    @Column(name = "summary_html", nullable = false, columnDefinition = "TEXT")
    private String summaryHtml;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Builder
    private Guide(Long policyId, String summaryHtml, String sourceHash) {
        this.policyId = policyId;
        this.summaryHtml = summaryHtml;
        this.sourceHash = sourceHash;
    }

    public boolean hasChanged(String newHash) {
        return !this.sourceHash.equals(newHash);
    }

    public void regenerate(String summaryHtml, String sourceHash) {
        this.summaryHtml = summaryHtml;
        this.sourceHash = sourceHash;
    }
}
