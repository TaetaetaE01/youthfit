package com.youthfit.user.application.dto.result;

public record EmailContent(
        String subject,
        String htmlBody,
        String textBody
) {
}
