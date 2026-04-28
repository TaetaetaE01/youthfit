package com.youthfit.common.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * 토큰/임베딩 비용 가드.
 * - allowlist 비어있으면: 전체 허용 (prod default).
 * - allowlist 채워져 있으면: 해당 정책만 허용. 그 외 정책의 LLM/임베딩 호출은 진입점에서 skip.
 * <p>
 * 사용 예: GuideGenerationService, RagIndexingService, AttachmentReindexService 진입점,
 * IngestionService 의 신청 기간 LLM 추출 호출 직전.
 */
@Component
@RequiredArgsConstructor
public class CostGuard {

    private static final Logger log = LoggerFactory.getLogger(CostGuard.class);

    private final CostGuardProperties properties;

    /** allowlist 가 비어있으면 cost-guard 비활성 (전체 허용). */
    public boolean enabled() {
        return !properties.parsedAllowlist().isEmpty();
    }

    /** 정책 ID 가 LLM 호출 허용 대상인지. allowlist 가 비어있으면 항상 true. */
    public boolean allows(Long policyId) {
        if (!enabled()) return true;
        if (policyId == null) return false;
        return properties.parsedAllowlist().contains(policyId);
    }

    /** 비허용 정책에 대한 skip 로그. 호출자가 reason 을 전달. */
    public void logSkip(String operation, Long policyId) {
        log.info("cost-guard skip: operation={}, policyId={}, allowlist={}",
                operation, policyId, properties.parsedAllowlist());
    }

    @Configuration
    @EnableConfigurationProperties(CostGuardProperties.class)
    static class Config {
    }
}
