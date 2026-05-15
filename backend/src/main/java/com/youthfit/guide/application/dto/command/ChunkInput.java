package com.youthfit.guide.application.dto.command;

/**
 * 가이드 생성 입력 청크 단위.
 *
 * @param content      청크 본문
 * @param attachmentId 첨부 ID. null 인 경우 BODY/ENRICHMENT_BODY 청크
 * @param pageStart    첨부 페이지 시작. null 인 경우 페이지 정보 없음
 * @param pageEnd      첨부 페이지 끝
 * @param source       청크 출처 라벨 — "BODY" / "ATTACHMENT" / "ENRICHMENT_BODY"
 *                     rag.domain.model.PolicyDocumentSource enum 의 .name() 결과를 그대로 사용한다.
 *                     guide 모듈이 rag enum 을 직접 import 하지 않도록 String 으로 매핑 (모듈 결합도 회피).
 */
public record ChunkInput(
        String content,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd,
        String source
) {}
