package com.youthfit.policy.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_source")
public class PolicySource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PolicySource(Policy policy, SourceType sourceType, String externalId,
                         String sourceUrl, String rawJson, String sourceHash) {
        this.policy = policy;
        this.sourceType = sourceType;
        this.externalId = externalId;
        this.sourceUrl = sourceUrl;
        this.rawJson = rawJson;
        this.sourceHash = sourceHash;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── 비즈니스 메서드 ──

    public boolean hasChanged(String newHash) {
        return !this.sourceHash.equals(newHash);
    }

    public void updateSource(String rawJson, String sourceHash) {
        this.rawJson = rawJson;
        this.sourceHash = sourceHash;
    }
}
