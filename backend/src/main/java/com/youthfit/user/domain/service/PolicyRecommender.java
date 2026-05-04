package com.youthfit.user.domain.service;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.user.domain.model.NotificationSetting;
import com.youthfit.user.domain.model.RegionSidoCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
public class PolicyRecommender {

    private static final int MAX_PICKS = 5;

    public List<Policy> filterByInterest(NotificationSetting setting, List<Policy> candidates) {
        Set<Category> categories = setting.getInterestCategories();
        Set<RegionSidoCode> regions = setting.getInterestRegions();

        return candidates.stream()
                .filter(p -> categories.isEmpty() || categories.contains(p.getCategory()))
                .filter(p -> regions.isEmpty() || regions.stream().anyMatch(r -> r.matches(p.getRegionCode())))
                .toList();
    }

    public List<Policy> sortAndLimit(List<Policy> policies) {
        return policies.stream()
                .sorted(Comparator
                        .comparing(Policy::getApplyEnd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Policy::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_PICKS)
                .toList();
    }
}
