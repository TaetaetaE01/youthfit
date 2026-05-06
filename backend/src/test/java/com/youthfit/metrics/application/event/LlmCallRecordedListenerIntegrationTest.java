package com.youthfit.metrics.application.event;

import com.youthfit.metrics.domain.model.LlmCostBucket;
import com.youthfit.metrics.domain.model.LlmModule;
import com.youthfit.metrics.domain.repository.LlmCostBucketRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트 → 1시간 버킷 누적 적재 통합 테스트.
 *
 * <p>본 프로젝트는 임베디드 DB 인프라(H2 등)가 없어 {@code @SpringBootTest} 가
 * PostgreSQL 연결을 시도하다 실패한다. 또한 upsert 쿼리가 PostgreSQL native syntax
 * ({@code INSERT ... ON CONFLICT}) 를 사용하므로 H2 로 대체할 수 없다.
 *
 * <p>Stage F (E2E 통합 검증) 에서 실 DB 환경이 갖춰진 뒤 {@code @Disabled} 를 제거한다.
 */
@Disabled("외부 인프라(PostgreSQL) 연결이 필요하여 CI에서 제외 — Stage F 에서 @Disabled 제거")
@SpringBootTest
@ActiveProfiles("test")
class LlmCallRecordedListenerIntegrationTest {

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    LlmCostBucketRepository repository;

    @Test
    void 같은_시간대_같은_모델_5번_호출_시_call_count_5_적재() throws InterruptedException {
        repository.deleteAll();
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            publisher.publishEvent(new LlmCallRecorded(
                    LlmModule.QNA, "gpt-4o-mini", 100, 50, now
            ));
        }

        // Awaitility 가 classpath 에 없으므로 Thread.sleep 으로 대체
        // (Awaitility 추가는 Stage F 에서 Testcontainers 셋업과 함께 수행)
        Thread.sleep(2000);

        List<LlmCostBucket> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getCallCount()).isEqualTo(5);
        assertThat(all.get(0).getPromptTokens()).isEqualTo(500);
        assertThat(all.get(0).getCompletionTokens()).isEqualTo(250);
    }
}
