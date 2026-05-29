package com.youthfit.admin.application.dto;

/**
 * 참조 사이트 1건의 펼침 영역 상세 결과.
 *
 * <p>Phase D 이전에는 채울 데이터가 없으므로 서비스에서 빈 리스트만 반환한다.
 * Phase D 에서 ENRICHMENT step 의 {@code detail_json.skippedUrls} 가 파싱되면
 * fetch 상태와 RAG chunk 개수가 채워진다.
 */
public record ReferenceDetailResult(
        String url,
        String status,
        int chunkCount
) {}
