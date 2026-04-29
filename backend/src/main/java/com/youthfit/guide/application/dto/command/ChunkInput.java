package com.youthfit.guide.application.dto.command;

/**
 * 가이드 생성 입력 청크 단위.
 *
 * <p>각 청크는 정책 본문(BODY) 또는 첨부(ATTACHMENT) 출처를 가지며,
 * 첨부 청크인 경우 attachmentId 와 (PDF인 경우) pageStart/pageEnd 를 보유한다.
 *
 * @param content      청크 본문
 * @param attachmentId 첨부 ID. null 인 경우 정책 본문 청크
 * @param pageStart    첨부 페이지 시작. null 인 경우 페이지 정보 없음 (HWP 등) 또는 본문 청크
 * @param pageEnd      첨부 페이지 끝. pageStart 가 null 이면 함께 null
 */
public record ChunkInput(
        String content,
        Long attachmentId,
        Integer pageStart,
        Integer pageEnd
) {}
