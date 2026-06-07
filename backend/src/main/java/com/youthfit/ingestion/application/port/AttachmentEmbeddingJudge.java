package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.application.dto.command.AttachmentEmbeddingJudgeCommand;
import com.youthfit.ingestion.application.dto.result.AttachmentEmbeddingResult;

/**
 * 정책 첨부가 RAG 임베딩에 가치 있는지 LLM 으로 판정하는 포트.
 * 구현은 infrastructure 의 OpenAI 어댑터.
 */
public interface AttachmentEmbeddingJudge {

    /**
     * 첨부별 임베딩 포함 여부를 판정한다.
     * @throws RuntimeException LLM 호출/파싱 실패 시. 호출자가 fail-open 으로 폴백한다.
     */
    AttachmentEmbeddingResult judge(AttachmentEmbeddingJudgeCommand command);
}
