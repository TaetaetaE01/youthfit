package com.youthfit.policy.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import com.youthfit.common.exception.ErrorCode;
import com.youthfit.common.exception.YouthFitException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy")
public class Policy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "support_target", columnDefinition = "TEXT")
    private String supportTarget;

    @Column(name = "selection_criteria", columnDefinition = "TEXT")
    private String selectionCriteria;

    @Column(name = "support_content", columnDefinition = "TEXT")
    private String supportContent;

    @Column(length = 200)
    private String organization;

    @Column(length = 300)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "apply_start")
    private LocalDate applyStart;

    @Column(name = "apply_end")
    private LocalDate applyEnd;

    @Column(name = "reference_year")
    private Integer referenceYear;

    @Column(name = "support_cycle", length = 100)
    private String supportCycle;

    @Column(name = "provide_type", length = 100)
    private String provideType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_sites", columnDefinition = "jsonb")
    private List<PolicyReferenceSite> referenceSites = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "apply_methods", columnDefinition = "jsonb")
    private List<PolicyApplyMethod> applyMethods = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_level", nullable = false, length = 10)
    private DetailLevel detailLevel;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "policy_life_tag", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "tag", length = 100)
    private Set<String> lifeTags = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "policy_theme_tag", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "tag", length = 100)
    private Set<String> themeTags = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "policy_target_tag", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "tag", length = 100)
    private Set<String> targetTags = new HashSet<>();

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PolicyAttachment> attachments = new ArrayList<>();

    @Builder
    private Policy(String title, String summary, String body,
                   String supportTarget, String selectionCriteria, String supportContent,
                   String organization, String contact,
                   Category category, String regionCode,
                   LocalDate applyStart, LocalDate applyEnd,
                   Integer referenceYear, String supportCycle, String provideType) {
        this.title = title;
        this.summary = summary;
        this.body = body;
        this.supportTarget = supportTarget;
        this.selectionCriteria = selectionCriteria;
        this.supportContent = supportContent;
        this.organization = organization;
        this.contact = contact;
        this.category = category;
        this.regionCode = regionCode;
        this.applyStart = applyStart;
        this.applyEnd = applyEnd;
        this.referenceYear = referenceYear;
        this.supportCycle = supportCycle;
        this.provideType = provideType;
        this.status = PolicyStatus.UPCOMING;
        this.detailLevel = DetailLevel.LITE;
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

    public void updateInfo(String title, String summary, String body,
                           String supportTarget, String selectionCriteria, String supportContent,
                           String organization, String contact,
                           Category category, String regionCode,
                           LocalDate applyStart, LocalDate applyEnd,
                           Integer referenceYear, String supportCycle, String provideType) {
        this.title = title;
        this.summary = summary;
        this.body = body;
        this.supportTarget = supportTarget;
        this.selectionCriteria = selectionCriteria;
        this.supportContent = supportContent;
        this.organization = organization;
        this.contact = contact;
        this.category = category;
        this.regionCode = regionCode;
        this.applyStart = applyStart;
        this.applyEnd = applyEnd;
        this.referenceYear = referenceYear;
        this.supportCycle = supportCycle;
        this.provideType = provideType;
    }

    public void replaceReferenceSites(List<PolicyReferenceSite> sites) {
        this.referenceSites = sites == null ? new ArrayList<>() : new ArrayList<>(sites);
    }

    public void replaceApplyMethods(List<PolicyApplyMethod> methods) {
        this.applyMethods = methods == null ? new ArrayList<>() : new ArrayList<>(methods);
    }

    public void replaceTags(Set<String> lifeTags, Set<String> themeTags, Set<String> targetTags) {
        this.lifeTags.clear();
        if (lifeTags != null) this.lifeTags.addAll(lifeTags);
        this.themeTags.clear();
        if (themeTags != null) this.themeTags.addAll(themeTags);
        this.targetTags.clear();
        if (targetTags != null) this.targetTags.addAll(targetTags);
    }

    public void replaceAttachments(List<PolicyAttachment> newAttachments) {
        this.attachments.clear();
        if (newAttachments == null) return;
        for (PolicyAttachment a : newAttachments) {
            a.assignTo(this);
            this.attachments.add(a);
        }
    }
}
