package com.youthfit.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 토큰/임베딩 비용 가드 설정.
 * policyAllowlist 가 비어있으면 전체 허용 (default, prod). 채워져 있으면 해당 정책 ID 만 LLM 호출 허용.
 * 의도: 개발 환경에서 새 정책 ingestion 시 자동으로 발생하는 가이드 생성·임베딩·첨부 추출 LLM 호출을
 * allowlist 정책으로만 제한하여 dev OpenAI 비용을 0 으로 묶기 위함.
 * <p>
 * 형식: 쉼표 구분 정책 ID (예: "7,30"). 빈 문자열 / 미설정이면 비활성 (전체 허용).
 */
@ConfigurationProperties(prefix = "youthfit.cost-guard")
public record CostGuardProperties(String policyAllowlist) {

    public List<Long> parsedAllowlist() {
        if (policyAllowlist == null || policyAllowlist.isBlank()) return List.of();
        return Arrays.stream(policyAllowlist.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }
}
