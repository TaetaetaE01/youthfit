package com.youthfit.eligibility.application.service;

import com.youthfit.eligibility.domain.model.EligibilityResult;
import com.youthfit.eligibility.domain.model.UncertainReason;
import com.youthfit.eligibility.domain.model.view.SummaryView;
import com.youthfit.eligibility.domain.service.CriterionEvaluation;

import java.util.List;

/**
 * 평가 결과 목록을 적합도 카드 헤더에 표시할 한 줄 요약 + 카운트로 변환한다.
 * 프레임워크 의존이 없는 stateless·thread-safe 헬퍼.
 *
 * <p>우선순위:
 * <ol>
 *   <li>미충족 ≥ 1 → "...조건이 맞지 않아요"</li>
 *   <li>미입력(MISSING_FIELD) ≥ 1 → "...정보가 더 필요해요"</li>
 *   <li>모호(AMBIGUOUS_SOURCE) ≥ 1 → "정책 원문이 모호한 조건이 N개 있어요"</li>
 *   <li>전부 통과 → "모든 조건을 충족해요"</li>
 * </ol>
 */
public class SummaryHeadlineGenerator {

    public SummaryView generate(List<CriterionEvaluation> evaluations) {
        List<CriterionEvaluation> ineligible = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.LIKELY_INELIGIBLE)
                .toList();
        List<CriterionEvaluation> missing = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.UNCERTAIN
                        && e.uncertainReason() == UncertainReason.MISSING_FIELD)
                .toList();
        List<CriterionEvaluation> ambiguous = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.UNCERTAIN
                        && e.uncertainReason() == UncertainReason.AMBIGUOUS_SOURCE)
                .toList();
        long eligibleCount = evaluations.stream()
                .filter(e -> e.result() == EligibilityResult.LIKELY_ELIGIBLE)
                .count();
        long uncertainCount = (long) missing.size() + ambiguous.size();

        String headline = buildHeadline(ineligible, missing, ambiguous);

        return new SummaryView(
                headline,
                (int) eligibleCount,
                (int) uncertainCount,
                ineligible.size()
        );
    }

    private String buildHeadline(
            List<CriterionEvaluation> ineligible,
            List<CriterionEvaluation> missing,
            List<CriterionEvaluation> ambiguous
    ) {
        if (ineligible.size() == 1) {
            return ineligible.get(0).label() + " 1개 조건이 맞지 않아요";
        }
        if (ineligible.size() > 1) {
            return ineligible.get(0).label() + " 등 " + ineligible.size() + "개 조건이 맞지 않아요";
        }
        if (missing.size() == 1) {
            return missing.get(0).label() + " 정보가 더 필요해요";
        }
        if (missing.size() > 1) {
            return missing.get(0).label() + " 등 " + missing.size() + "개 정보가 더 필요해요";
        }
        if (!ambiguous.isEmpty()) {
            return "정책 원문이 모호한 조건이 " + ambiguous.size() + "개 있어요";
        }
        return "모든 조건을 충족해요";
    }
}
