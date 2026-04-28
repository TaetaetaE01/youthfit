package com.youthfit.policy.domain.model;

import java.util.Map;
import java.util.Optional;

public record IncomeBracketReference(
        int year,
        int version,
        Map<HouseholdSize, Map<Integer, Long>> medianIncome,
        Map<HouseholdSize, Long> nearPoor
) {

    public IncomeBracketReference {
        medianIncome = medianIncome == null ? Map.of() : Map.copyOf(medianIncome);
        nearPoor = nearPoor == null ? Map.of() : Map.copyOf(nearPoor);
    }

    public Optional<Long> findAmount(HouseholdSize size, int percent) {
        Map<Integer, Long> bySize = medianIncome.get(size);
        if (bySize == null) return Optional.empty();
        return Optional.ofNullable(bySize.get(percent));
    }
}
