package com.youthfit.policy.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import com.youthfit.common.domain.PeriodSource;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Column(name = "normalized_title", insertable = false, updatable = false)
    private String normalizedTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "region_codes", columnDefinition = "TEXT")
    private String regionCodes;

    @Column(name = "apply_start")
    private LocalDate applyStart;

    @Column(name = "apply_end")
    private LocalDate applyEnd;

    @Column(name = "apply_period_source", length = 32)
    @Enumerated(EnumType.STRING)
    private PeriodSource applyPeriodSource;

    @Column(name = "apply_period_confidence")
    private Double applyPeriodConfidence;

    @Column(name = "apply_period_evidence", length = 200)
    private String applyPeriodEvidence;

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

    @Column(name = "screening_method", columnDefinition = "TEXT")
    private String screeningMethod;

    @Column(name = "submission_documents", columnDefinition = "TEXT")
    private String submissionDocuments;

    @Column(name = "additional_qualification", columnDefinition = "TEXT")
    private String additionalQualification;

    @Column(name = "participation_restriction", columnDefinition = "TEXT")
    private String participationRestriction;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    @Column(name = "business_period_start")
    private LocalDate businessPeriodStart;

    @Column(name = "business_period_end")
    private LocalDate businessPeriodEnd;

    @Column(name = "business_period_note", columnDefinition = "TEXT")
    private String businessPeriodNote;

    @Column(name = "support_scale")
    private Integer supportScale;

    @Column(name = "first_come_first_served", nullable = false)
    private boolean firstComeFirstServed;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PolicyAttachment> attachments = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enrichment", columnDefinition = "jsonb")
    private PolicyEnrichment enrichment;

    @Builder
    private Policy(String title, String summary, String body,
                   String supportTarget, String selectionCriteria, String supportContent,
                   String organization, String contact,
                   Category category, String regionCode, List<String> regionCodes,
                   LocalDate applyStart, LocalDate applyEnd,
                   // 신청기간 추출 메타 (Task 11/12)
                   PeriodSource applyPeriodSource,
                   Double applyPeriodConfidence,
                   String applyPeriodEvidence,
                   Integer referenceYear, String supportCycle, String provideType,
                   String screeningMethod, String submissionDocuments,
                   String additionalQualification, String participationRestriction,
                   String additionalNotes,
                   LocalDate businessPeriodStart, LocalDate businessPeriodEnd,
                   String businessPeriodNote,
                   Integer supportScale, boolean firstComeFirstServed, String applyUrl) {
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
        this.regionCodes = joinRegionCodes(regionCodes);
        this.applyStart = applyStart;
        this.applyEnd = applyEnd;
        this.applyPeriodSource = applyPeriodSource;
        this.applyPeriodConfidence = applyPeriodConfidence;
        this.applyPeriodEvidence = applyPeriodEvidence;
        this.referenceYear = referenceYear;
        this.supportCycle = supportCycle;
        this.provideType = provideType;
        this.screeningMethod = screeningMethod;
        this.submissionDocuments = submissionDocuments;
        this.additionalQualification = additionalQualification;
        this.participationRestriction = participationRestriction;
        this.additionalNotes = additionalNotes;
        this.businessPeriodStart = businessPeriodStart;
        this.businessPeriodEnd = businessPeriodEnd;
        this.businessPeriodNote = businessPeriodNote;
        this.supportScale = supportScale;
        this.firstComeFirstServed = firstComeFirstServed;
        this.applyUrl = applyUrl;
        this.status = PolicyStatus.UPCOMING;
        this.detailLevel = DetailLevel.LITE;
    }

    public List<String> getRegionCodeList() {
        if (this.regionCodes == null || this.regionCodes.isBlank()) return List.of();
        return Arrays.stream(this.regionCodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String joinRegionCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return null;
        return codes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
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

    /** 사실상 상시로 분류할 최소 신청 기간 (일). 약 9개월. */
    public static final int EFFECTIVELY_ALWAYS_OPEN_MIN_DAYS = 270;

    /**
     * 캘린더 표시에서 "사실상 상시" 로 분류할지 판정.
     * end 가 ?-12-31 이고 신청 가능 기간이 약 9개월 (270일) 이상이면 true.
     * 진짜 상시 (start, end 모두 null) 는 이 메서드의 책임이 아니다.
     */
    public boolean isEffectivelyAlwaysOpen() {
        if (applyEnd == null) return false;
        if (applyEnd.getMonthValue() != 12 || applyEnd.getDayOfMonth() != 31) return false;
        if (applyStart == null) return true;
        return ChronoUnit.DAYS.between(applyStart, applyEnd) >= EFFECTIVELY_ALWAYS_OPEN_MIN_DAYS;
    }

    public void updateInfo(String title, String summary, String body,
                           String supportTarget, String selectionCriteria, String supportContent,
                           String organization, String contact,
                           Category category, String regionCode, List<String> regionCodes,
                           LocalDate applyStart, LocalDate applyEnd,
                           Integer referenceYear, String supportCycle, String provideType,
                           String screeningMethod, String submissionDocuments,
                           String additionalQualification, String participationRestriction,
                           String additionalNotes,
                           LocalDate businessPeriodStart, LocalDate businessPeriodEnd,
                           String businessPeriodNote,
                           Integer supportScale, boolean firstComeFirstServed, String applyUrl) {
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
        this.regionCodes = joinRegionCodes(regionCodes);
        this.applyStart = applyStart;
        this.applyEnd = applyEnd;
        this.referenceYear = referenceYear;
        this.supportCycle = supportCycle;
        this.provideType = provideType;
        this.screeningMethod = screeningMethod;
        this.submissionDocuments = submissionDocuments;
        this.additionalQualification = additionalQualification;
        this.participationRestriction = participationRestriction;
        this.additionalNotes = additionalNotes;
        this.businessPeriodStart = businessPeriodStart;
        this.businessPeriodEnd = businessPeriodEnd;
        this.businessPeriodNote = businessPeriodNote;
        this.supportScale = supportScale;
        this.firstComeFirstServed = firstComeFirstServed;
        this.applyUrl = applyUrl;
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

    public void replaceEnrichment(PolicyEnrichment newEnrichment) {
        this.enrichment = newEnrichment;
    }

    public void updateApplyPeriod(
            LocalDate start, LocalDate end,
            PeriodSource source, Double confidence, String evidence) {
        this.applyStart = start;
        this.applyEnd = end;
        this.applyPeriodSource = source;
        this.applyPeriodConfidence = confidence;
        this.applyPeriodEvidence = evidence;
    }
}
