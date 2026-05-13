package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.command.PolicyMetadata;

import java.util.List;
import java.util.function.Consumer;

public interface QnaLlmProvider {

    String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer);

    /**
     * 답변 본문과 RAG 컨텍스트를 받아 같은 정책 본문 안에서 실제 답변 가능한 후속 추천 질문 2~3개를 생성한다.
     * context 에 단서가 없는 일반론적 질문(예: 본문 미존재 정보)을 추천하지 않도록 grounding 한다.
     * 실패 시 빈 리스트를 반환하거나 RuntimeException 을 던질 수 있다 — 호출자가 graceful degrade.
     */
    List<String> generateFollowUpQuestions(String policyTitle, String question, String answer, String context);
}
