package com.youthfit.qna.application.port;

import java.util.Optional;

/**
 * 사용자 질문을 정책 도메인 표준 용어로 재작성하는 포트.
 *
 * <p>구현체는 LLM 호출 실패·timeout·검증 실패 시 {@link Optional#empty()} 를 반환해야 한다.
 * 호출자는 empty 일 때 원래 질문으로 fallback 한다.
 */
public interface QueryRewriter {

    /**
     * @param policyTitle 정책명 (rewrite context 에 포함)
     * @param userQuestion 사용자 원래 질문
     * @return 재작성된 검색 query, 또는 빈 결과면 {@link Optional#empty()}
     */
    Optional<String> rewrite(String policyTitle, String userQuestion);
}
