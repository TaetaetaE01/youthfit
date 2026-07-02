package com.youthfit.qna.domain.model;

/**
 * 근거를 찾지 못했을 때의 fallback 답변 단일 진실 소스.
 * 프롬프트 지시문(MESSAGE)과 출력 판정(isFallback)이 이 클래스 하나를 참조한다 —
 * 문구를 바꿀 때 MESSAGE 와 MARKER 가 함께 유지되는지 확인할 것.
 */
public final class QnaFallbackAnswer {

    /** LLM 에게 지시하는 fallback 답변 전문. */
    public static final String MESSAGE =
            "해당 정책 원문에 관련 내용이 명시되어 있지 않습니다. 공식 문의처에서 확인하시는 것을 권장합니다.";

    /**
     * fallback 출력 검출용 부분 문자열. LLM 이 문구를 변형해 출력하는 경우가 있어
     * MESSAGE 전체 일치가 아닌 핵심 구절 포함 여부로 판정한다.
     */
    static final String MARKER = "명시되어 있지 않";

    private QnaFallbackAnswer() {
    }

    /** LLM 답변이 fallback 메시지인지 판정한다. */
    public static boolean isFallback(String answer) {
        return answer != null && answer.contains(MARKER);
    }
}
