package com.youthfit.admin.presentation.dto.response;

import java.time.LocalDateTime;

public record EmailAttemptDetailResponse(
    Long id, Long notificationHistoryId, String recipient, Long recipientUserId,
    String emailType, String subject, String inputPayloadJson,
    String sesMessageId, String status,
    String errorCode, String errorMessage, String bounceType,
    LocalDateTime sentAt, LocalDateTime updatedAt
) { }
