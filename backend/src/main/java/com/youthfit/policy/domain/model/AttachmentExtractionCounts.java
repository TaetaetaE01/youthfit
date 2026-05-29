package com.youthfit.policy.domain.model;

/**
 * 정책 1건의 첨부 extraction_status 카운트 집계.
 *
 * <ul>
 *     <li>{@code total}: 전체 첨부 수</li>
 *     <li>{@code downloaded}: extraction_status &gt;= DOWNLOADED 인 카운트
 *         (DOWNLOADED + EXTRACTING + EXTRACTED + FAILED + SKIPPED — PENDING/DOWNLOADING 이후)</li>
 *     <li>{@code extracted}: extraction_status = EXTRACTED 인 카운트</li>
 * </ul>
 */
public record AttachmentExtractionCounts(
        long total,
        long downloaded,
        long extracted
) {
    public static AttachmentExtractionCounts empty() {
        return new AttachmentExtractionCounts(0L, 0L, 0L);
    }
}
