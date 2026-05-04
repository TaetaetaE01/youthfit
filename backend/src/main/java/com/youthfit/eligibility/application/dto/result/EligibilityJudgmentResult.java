package com.youthfit.eligibility.application.dto.result;

import com.youthfit.eligibility.domain.model.view.SummaryView;

public record EligibilityJudgmentResult(
        Long policyId,
        String policyTitle,
        String overallResult,
        SummaryView summary,
        GroupedCriteria criteria,
        String disclaimer
) {

    public static final String DISCLAIMER_TEXT =
            "본 결과는 참고용이며, 법적 효력이 있는 자격 판정이 아닙니다. 최종 확인은 공식 신청 채널에서 진행해 주세요.";
}
