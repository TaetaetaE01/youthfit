package com.youthfit.guide.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, columnDefinition = "jsonb")
    private GuideContent content;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Builder
    private Guide(Long policyId, GuideContent content, String sourceHash) {
        this.policyId = policyId;
        this.content = content;
        this.sourceHash = sourceHash;
    }

    public boolean hasChanged(String newHash) {
        return !this.sourceHash.equals(newHash);
    }

    public void regenerate(GuideContent content, String sourceHash) {
        this.content = content;
        this.sourceHash = sourceHash;
    }
}
