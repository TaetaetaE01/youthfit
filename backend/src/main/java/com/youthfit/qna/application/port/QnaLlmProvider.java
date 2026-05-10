package com.youthfit.qna.application.port;

import com.youthfit.qna.application.dto.command.PolicyMetadata;

import java.util.List;
import java.util.function.Consumer;

public interface QnaLlmProvider {

    String generateAnswer(String policyTitle, PolicyMetadata metadata, String context, String question, Consumer<String> chunkConsumer);

    /**
     * 답변 본문을 받아 같은 정책 맥락에서 이어갈 후속 추천 질문 2~3개를 생성한다.
     * 실패 시 빈 리스트를 반환하거나 RuntimeException을 던질 수 있다 — 호출자가 graceful degrade.
     */
    List<String> generateFollowUpQuestions(String policyTitle, String question, String answer);
}
