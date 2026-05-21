package com.youthfit.ingestion.application.port;

import com.youthfit.ingestion.application.dto.command.IngestPolicyCommand;
import com.youthfit.policy.domain.model.Policy;

import java.time.LocalDate;
import java.util.List;

public record PeriodExtractionContext(
        String title,
        String body,
        LocalDate n8nApplyStart,
        LocalDate n8nApplyEnd,
        String externalId,
        List<String> attachmentTexts
) {
    public static PeriodExtractionContext forIngest(IngestPolicyCommand c) {
        return new PeriodExtractionContext(
                c.title(), c.body(),
                c.applyStart(), c.applyEnd(),
                c.externalId(),
                List.of()
        );
    }

    /**
     * 보강 시점 컨텍스트. externalId 는 Policy 엔티티에 없으므로 null.
     * 로그에서는 policy.getId() 를 사용한다 (PeriodBackfillService 에서 호출 시 별도 로깅).
     */
    public static PeriodExtractionContext forBackfill(Policy p, List<String> attachmentTexts) {
        return new PeriodExtractionContext(
                p.getTitle(),
                p.getBody(),
                p.getApplyStart(),
                p.getApplyEnd(),
                null,
                attachmentTexts == null ? List.of() : attachmentTexts
        );
    }
}
