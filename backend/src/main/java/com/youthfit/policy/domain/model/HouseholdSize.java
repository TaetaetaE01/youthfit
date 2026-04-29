package com.youthfit.policy.domain.model;

public enum HouseholdSize {
    ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8);

    private final int count;

    HouseholdSize(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }

    public static HouseholdSize fromCount(int count) {
        for (HouseholdSize s : values()) {
            if (s.count == count) return s;
        }
        throw new IllegalArgumentException("지원하지 않는 가구원 수: " + count);
    }
}
