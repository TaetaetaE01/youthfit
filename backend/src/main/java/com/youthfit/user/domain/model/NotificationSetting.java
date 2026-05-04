package com.youthfit.user.domain.model;

import com.youthfit.common.domain.BaseTimeEntity;
import com.youthfit.policy.domain.model.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_setting")
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "days_before_deadline", nullable = false)
    private int daysBeforeDeadline;

    @Column(name = "recommendation_enabled", nullable = false)
    private boolean recommendationEnabled;

    @ElementCollection(targetClass = Category.class, fetch = FetchType.LAZY)
    @CollectionTable(
            name = "notification_interest_category",
            joinColumns = @JoinColumn(name = "notification_setting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private Set<Category> interestCategories = new HashSet<>();

    @ElementCollection(targetClass = RegionSidoCode.class, fetch = FetchType.LAZY)
    @CollectionTable(
            name = "notification_interest_region",
            joinColumns = @JoinColumn(name = "notification_setting_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sido_code", length = 10, nullable = false)
    private Set<RegionSidoCode> interestRegions = new HashSet<>();

    public NotificationSetting(Long userId) {
        this.userId = userId;
        this.emailEnabled = true;
        this.daysBeforeDeadline = 7;
        this.recommendationEnabled = false;
    }

    public void updateSetting(boolean emailEnabled, int daysBeforeDeadline,
                              boolean recommendationEnabled) {
        this.emailEnabled = emailEnabled;
        this.daysBeforeDeadline = daysBeforeDeadline;
        this.recommendationEnabled = recommendationEnabled;
    }

    public void replaceInterestCategories(Set<Category> categories) {
        this.interestCategories.clear();
        if (categories != null) {
            this.interestCategories.addAll(categories);
        }
    }

    public void replaceInterestRegions(Set<RegionSidoCode> regions) {
        this.interestRegions.clear();
        if (regions != null) {
            this.interestRegions.addAll(regions);
        }
    }

    public boolean canDispatchRecommendation(EligibilityProfile profile) {
        if (!recommendationEnabled) return false;
        if (interestCategories.isEmpty() && interestRegions.isEmpty()) return false;
        if (profile == null) return false;
        if (profile.getLegalDongCode() == null || profile.getAge() == null) return false;
        return true;
    }
}
