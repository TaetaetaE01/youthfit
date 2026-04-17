package com.youthfit.ingestion.domain.service;

import com.youthfit.ingestion.domain.model.PolicyPeriod;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PolicyPeriodExtractor {

    private static final Pattern DATE_RANGE = Pattern.compile(
            "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*[일]?\\.?"
                    + "\\s*[~〜∼\\-]\\s*"
                    + "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*[일]?\\.?"
    );

    public PolicyPeriod extract(String body) {
        if (body == null || body.isBlank()) return PolicyPeriod.empty();
        Matcher matcher = DATE_RANGE.matcher(body);
        while (matcher.find()) {
            try {
                LocalDate start = LocalDate.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                );
                LocalDate end = LocalDate.of(
                        Integer.parseInt(matcher.group(4)),
                        Integer.parseInt(matcher.group(5)),
                        Integer.parseInt(matcher.group(6))
                );
                if (!end.isBefore(start)) {
                    return PolicyPeriod.of(start, end);
                }
            } catch (DateTimeException | NumberFormatException ignored) {
                // 다음 매치 시도
            }
        }
        return PolicyPeriod.empty();
    }
}
