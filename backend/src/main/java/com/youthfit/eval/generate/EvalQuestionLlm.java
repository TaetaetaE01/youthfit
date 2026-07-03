package com.youthfit.eval.generate;

import java.util.List;

/**
 * 청크 내용으로 답할 수 있는 평가용 질문을 역생성하는 포트.
 * 실패 시 빈 리스트를 반환한다 (호출자는 해당 정책을 스킵).
 */
public interface EvalQuestionLlm {

    List<GeneratedEvalQuestion> generateQuestions(String policyTitle, List<String> chunkContents);
}
