package com.youthfit.admin.presentation.dto.request;

import com.youthfit.qna.domain.model.LookupResultType;

import java.time.LocalDateTime;

public record QnaCacheLookupListQuery(
        LookupResultType result,
        Long policyId,
        LocalDateTime from,
        LocalDateTime to,
        int page,
        int size
) {
    public QnaCacheLookupListQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
    }
}
