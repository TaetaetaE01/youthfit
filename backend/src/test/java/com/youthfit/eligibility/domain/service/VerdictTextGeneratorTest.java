package com.youthfit.eligibility.domain.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.RequirementView;
import com.youthfit.eligibility.domain.model.view.UserValueView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VerdictTextGenerator")
class VerdictTextGeneratorTest {

    private final VerdictTextGenerator generator = new VerdictTextGenerator();

    @Test
    @DisplayName("LIKELY_ELIGIBLE → \"{label} 조건을 충족해요\"")
    void eligible() {
        String text = generator.generate(
                EligibilityResult.LIKELY_ELIGIBLE,
                null,
                "연령",
                new RequirementView("BETWEEN", "만 19세 이상 34세 이하"),
                new UserValueView("29", "만 29세")
        );

        assertThat(text).isEqualTo("연령 조건을 충족해요");
    }

    @Test
    @DisplayName("LIKELY_INELIGIBLE → \"정책은 ___을 요구하는데, 내 정보는 ___이에요\"")
    void ineligible() {
        String text = generator.generate(
                EligibilityResult.LIKELY_INELIGIBLE,
                null,
                "고용 형태",
                new RequirementView("IN", "재직자, 자영업자, 프리랜서, 일용근로자, 단기근로자"),
                new UserValueView("UNEMPLOYED", "미취업자")
        );

        assertThat(text).isEqualTo(
                "정책은 재직자, 자영업자, 프리랜서, 일용근로자, 단기근로자를 요구하는데, 내 정보는 미취업자이에요"
        );
    }

    @Test
    @DisplayName("UNCERTAIN + MISSING_FIELD → \"{label} 정보가 없어요\"")
    void uncertainMissing() {
        String text = generator.generate(
                EligibilityResult.UNCERTAIN,
                UncertainReason.MISSING_FIELD,
                "가구 소득",
                new RequirementView("LTE", "5,000만원 이하"),
                null
        );

        assertThat(text).isEqualTo("가구 소득 정보가 없어요");
    }

    @Test
    @DisplayName("UNCERTAIN + AMBIGUOUS_SOURCE → \"정책 원문이 모호해 단정하기 어려워요\"")
    void uncertainAmbiguous() {
        String text = generator.generate(
                EligibilityResult.UNCERTAIN,
                UncertainReason.AMBIGUOUS_SOURCE,
                "학력",
                new RequirementView("EQ", "대학 졸업"),
                new UserValueView("COLLEGE_GRAD", "대학 졸업")
        );

        assertThat(text).isEqualTo("정책 원문이 모호해 단정하기 어려워요");
    }
}
