package com.youthfit.rag.domain.model;

/**
 * PolicyDocument 청크의 출처 구분.
 *
 * <ul>
 *     <li>{@link #BODY} — 정책 본문 컬럼(body / supportTarget / supportContent 등)</li>
 *     <li>{@link #ATTACHMENT} — 첨부 파일에서 추출된 텍스트</li>
 *     <li>{@link #ENRICHMENT_BODY} — 외부 정책 안내 페이지 본문(enrichment.cleanedText)</li>
 * </ul>
 */
public enum PolicyDocumentSource {
    BODY,
    ATTACHMENT,
    ENRICHMENT_BODY
}
