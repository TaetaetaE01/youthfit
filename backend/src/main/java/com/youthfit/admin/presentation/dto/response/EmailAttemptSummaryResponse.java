package com.youthfit.admin.presentation.dto.response;

import java.time.LocalDateTime;

public record EmailAttemptSummaryResponse(
    Long id, String recipient, String emailType, String status,
    String subject, LocalDateTime sentAt, LocalDateTime updatedAt,
    String sesMessageId
) { }
