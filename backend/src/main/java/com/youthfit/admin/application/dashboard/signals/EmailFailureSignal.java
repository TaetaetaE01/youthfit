package com.youthfit.admin.application.dashboard.signals;

import com.youthfit.admin.application.dashboard.DashboardSeverity;
import com.youthfit.admin.application.dashboard.DashboardSignal;
import com.youthfit.admin.application.dashboard.DashboardSignalResult;
import com.youthfit.admin.application.dashboard.DashboardThresholds;
import com.youthfit.user.domain.model.EmailSendStatus;
import com.youthfit.user.domain.repository.EmailSendAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 최근 24시간 동안 이메일 발송 실패율 또는 절대 실패 건수가 임계치를 초과하면 HIGH 신호를 발화한다.
 *
 * <p>{@code FAILED} 와 {@code BOUNCED} 를 "실패"로 간주한다. {@code COMPLAINED} 는 사용자 스팸 신고로
 * 전달 자체는 성공한 경우이므로 제외한다.</p>
 */
@Component
@RequiredArgsConstructor
public class EmailFailureSignal implements DashboardSignal {

    private static final String CODE = "EMAIL_FAILURE";
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final String DEEPLINK = "/admin/email?filter=failed";
    private static final Set<EmailSendStatus> FAILED_STATUSES =
            EnumSet.of(EmailSendStatus.FAILED, EmailSendStatus.BOUNCED);

    private final EmailSendAttemptRepository repository;
    private final DashboardThresholds thresholds;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<DashboardSignalResult> evaluate(Instant now) {
        LocalDateTime since = LocalDateTime.ofInstant(now.minus(WINDOW), ZoneOffset.UTC);

        long totalSent = repository.countSentSince(since);
        long failedCount = repository.countByStatusSince(since, FAILED_STATUSES);

        if (totalSent == 0L) {
            return Optional.empty();
        }

        BigDecimal failureRate = BigDecimal.valueOf(failedCount)
                .divide(BigDecimal.valueOf(totalSent), 4, RoundingMode.HALF_UP);

        boolean rateExceeded = failureRate.compareTo(thresholds.getEmail().getFailureRateThreshold()) > 0;
        boolean countExceeded = failedCount > thresholds.getEmail().getFailureCountThreshold();

        if (!rateExceeded && !countExceeded) {
            return Optional.empty();
        }

        BigDecimal failureRatePercent = failureRate
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        String title = "이메일 실패 " + failedCount + "건 / " + totalSent + "건 (" + failureRatePercent + "%)";

        return Optional.of(new DashboardSignalResult(
                CODE,
                DashboardSeverity.HIGH,
                title,
                null,
                DEEPLINK,
                now
        ));
    }
}
