package com.youthfit.eligibility.application.port;

import com.youthfit.eligibility.application.dto.command.EligibilityRuleExtractionInput;
import com.youthfit.eligibility.application.dto.result.RawExtractedRule;

import java.util.List;

public interface EligibilityRuleLlmProvider {

    /**
     * 정책 원문에서 적합도 룰을 추출한다.
     *
     * @return 검증 전 raw 룰 목록. LLM 이 룰을 못 찾으면 빈 목록.
     * @throws IllegalStateException LLM 호출 실패 또는 응답 JSON 파싱 실패
     */
    List<RawExtractedRule> extractRules(EligibilityRuleExtractionInput input);

    /**
     * 검증 위반 피드백을 받아 동일 입력으로 재추출.
     */
    List<RawExtractedRule> regenerateWithFeedback(
            EligibilityRuleExtractionInput input,
            List<String> feedbackMessages);
}
