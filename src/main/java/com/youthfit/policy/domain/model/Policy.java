package com.youthfit.policy.domain.model;

import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "apply_start")
    private LocalDate applyStart;

    @Column(name = "apply_end")
    private LocalDate applyEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_level", nullable = false, length = 10)
    private DetailLevel detailLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Policy(String title, String summary, Category category,
                   String regionCode, LocalDate applyStart, LocalDate applyEnd) {
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.regionCode = regionCode;
        this.applyStart = applyStart;
        this.applyEnd = applyEnd;
        this.status = PolicyStatus.UPCOMING;
        this.detailLevel = DetailLevel.LITE;
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

    public void open() {
        if (this.status != PolicyStatus.UPCOMING) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "UPCOMING 상태에서만 모집 시작 가능합니다");
        }
        this.status = PolicyStatus.OPEN;
    }

    public void close() {
        if (this.status != PolicyStatus.OPEN) {
            throw new YouthFitException(ErrorCode.INVALID_INPUT, "OPEN 상태에서만 마감 가능합니다");
        }
        this.status = PolicyStatus.CLOSED;
    }

    public void upgradeDetailLevel(DetailLevel newLevel) {
        if (newLevel.ordinal() <= this.detailLevel.ordinal()) {
            return;
        }
        this.detailLevel = newLevel;
    }

    public boolean isOpen() {
        return this.status == PolicyStatus.OPEN;
    }

    public boolean isExpired() {
        return this.applyEnd != null && this.applyEnd.isBefore(LocalDate.now());
    }
}
