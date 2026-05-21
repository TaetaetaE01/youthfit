package com.youthfit.ingestion.domain.service.port;

import java.time.LocalDate;
import java.util.List;

/**
 * PeriodResolver 의 입력 컨텍스트. 순수 값 객체 — application/policy 모듈에 의존하지 않는다.
 * caller (IngestionService, PeriodBackfillService) 가 자신의 입력 (IngestPolicyCommand 또는
 * Policy) 에서 필요한 값만 뽑아 직접 생성한다.
 */
public record PeriodExtractionContext(
        String title,
        String body,
        LocalDate n8nApplyStart,
        LocalDate n8nApplyEnd,
        String externalId,
        List<String> attachmentTexts
) {
    public PeriodExtractionContext {
        if (attachmentTexts == null) {
            attachmentTexts = List.of();
        }
    }
}
