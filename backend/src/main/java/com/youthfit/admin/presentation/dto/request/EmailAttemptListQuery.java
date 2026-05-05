package com.youthfit.admin.presentation.dto.request;

import com.youthfit.user.domain.model.EmailSendStatus;
import com.youthfit.user.domain.model.NotificationType;

import java.time.LocalDate;
import java.util.List;

public record EmailAttemptListQuery(
    LocalDate from,
    LocalDate to,
    List<EmailSendStatus> statuses,
    NotificationType emailType,
    String recipient,
    int page,
    int size
) {
    public EmailAttemptListQuery {
        if (size <= 0 || size > 200) size = 20;
        if (page < 0) page = 0;
    }
}
