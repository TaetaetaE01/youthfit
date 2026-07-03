package com.youthfit.eval.generate;

import java.util.List;

/**
 * 대부분의 정책 원문에 근거가 없는 공용 NEGATIVE 질문 풀.
 * LLM 호출 없이 정책당 1개를 결정적으로 배정한다 (재실행 시 동일 배정).
 */
public final class NegativeQuestionPool {

    private static final List<String> QUESTIONS = List.of(
            "신청하면 며칠 만에 지급되나요?",
            "탈락하면 재심사를 요청할 수 있나요?",
            "외국인 배우자도 같이 신청 가능한가요?",
            "지원금을 받으면 세금 신고를 해야 하나요?",
            "작년에 받았으면 올해 또 받을 수 있나요?",
            "대리인이 대신 신청해도 되나요?"
    );

    private NegativeQuestionPool() {
    }

    public static String pick(Long policyId) {
        return QUESTIONS.get(Math.floorMod(policyId.intValue(), QUESTIONS.size()));
    }
}
