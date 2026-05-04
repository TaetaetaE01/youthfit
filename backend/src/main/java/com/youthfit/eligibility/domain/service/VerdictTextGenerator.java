package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.UserValueView;

/**
 * 평가 결과 한 건을 사용자에게 보여줄 자연어 한 줄로 변환한다.
 * 프레임워크 의존이 없는 순수 도메인 서비스이며 stateless·thread-safe 하다.
 *
 * <p>v1: 조사(을/를, 이/가) 종성 받침 처리는 단순화한다.
 */
public class VerdictTextGenerator {

    public String generate(
            EligibilityResult result,
            UncertainReason uncertainReason,
            String label,
            RequirementView requirement,
            UserValueView userValue
    ) {
        return switch (result) {
            case LIKELY_ELIGIBLE -> label + " 조건을 충족해요";
            case LIKELY_INELIGIBLE -> "정책은 " + requirement.displayText()
                    + "를 요구하는데, 내 정보는 " + userValue.displayText() + "이에요";
            case UNCERTAIN -> uncertainReason == UncertainReason.AMBIGUOUS_SOURCE
                    ? "정책 원문이 모호해 단정하기 어려워요"
                    : label + " 정보가 없어요";
        };
    }
}
