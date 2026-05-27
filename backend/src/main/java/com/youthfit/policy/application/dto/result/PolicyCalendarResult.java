package com.youthfit.policy.application.dto.result;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;

public record PolicyCalendarResult(
        Long id,
        String title,
        Category category,
        LocalDate applyStart,
        LocalDate applyEnd,
        String regionLabel,
        Integer deadlineYear
) {
    /**
     * Policy 를 PolicyCalendarResult 로 변환.
     * deadlineYear 는 isEffectivelyAlwaysOpen()=true 일 때만 채워진다.
     * 진짜 상시 (applyStart, applyEnd 모두 null) 와 일반 비-상시 정책은 모두 deadlineYear=null.
     * 호출자가 둘을 구분하려면 applyStart/applyEnd null 여부를 함께 확인해야 한다.
     */
    public static PolicyCalendarResult from(Policy policy, String regionLabel) {
        Integer deadlineYear = policy.isEffectivelyAlwaysOpen()
                ? policy.getApplyEnd().getYear()
                : null;
        return new PolicyCalendarResult(
                policy.getId(),
                policy.getTitle(),
                policy.getCategory(),
                policy.getApplyStart(),
                policy.getApplyEnd(),
                regionLabel,
                deadlineYear
        );
    }
}
