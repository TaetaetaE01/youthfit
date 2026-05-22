package com.youthfit.admin.application.service;

import com.youthfit.admin.application.dashboard.AreaStatus;
import com.youthfit.admin.application.dto.result.DashboardOverviewResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어드민 대시보드 overview wiring 통합 테스트.
 *
 * <p>{@link AdminDashboardOverviewService} 가 Spring 컨텍스트에서 정상 로딩되고
 * 9개 {@code DashboardSignal} 빈이 {@code DashboardSignalEvaluator} 에 자동 수집되어
 * 6개 영역 카드를 정의된 순서(ingestion → enrichment → llm-cost → email → qna-cache →
 * policy-intake)대로 반환하는지 검증한다.</p>
 *
 * <p>본 프로젝트는 임베디드 DB 인프라(H2 등)가 없어 {@code @SpringBootTest} 가
 * PostgreSQL/Redis 연결을 시도하다 실패한다(예: {@link com.youthfit.metrics.application.event.LlmCallRecordedListenerIntegrationTest}).
 * 또한 {@code policy.reference_sites}, LLM 비용 버킷 upsert 등 PostgreSQL native
 * 기능을 사용하므로 H2 로 대체할 수 없다.</p>
 *
 * <p>운영 PostgreSQL + Redis 가 갖춰진 E2E 검증 환경(local docker-compose, Stage F)
 * 에서 {@code @Disabled} 를 제거하면 그대로 통과해야 한다. 각 신호별 트리거 시나리오는
 * 단위 테스트({@link com.youthfit.admin.application.dashboard.DashboardSignalEvaluatorTest}
 * 및 9종 signal 단위 테스트)에서 임계치 경계까지 이미 커버하므로, 본 테스트는 wiring
 * 검증만 목적으로 한다.</p>
 */
@Disabled("외부 인프라(PostgreSQL + Redis) 연결이 필요하여 CI에서 제외 — 로컬 docker-compose 환경에서 @Disabled 제거 후 검증")
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AdminDashboardOverviewService 통합 (wiring)")
class AdminDashboardOverviewServiceIntegrationTest {

    @Autowired
    AdminDashboardOverviewService service;

    @Test
    @DisplayName("7개 영역 카드가 정의된 순서로 반환된다")
    void returns_seven_areas_in_expected_order() {
        DashboardOverviewResult r = service.findOverview();

        assertThat(r.areas()).hasSize(7);
        assertThat(r.areas()).extracting("key")
                .containsExactly("ingestion", "enrichment", "llm-cost", "email", "qna-cache", "policy-intake", "scheduled-tasks");
        // 빈 DB(또는 기본 테스트 데이터) → 각 카드는 OK 또는 CRITICAL/WARN 중 하나여야 한다(상태값 자체는 평가 가능).
        assertThat(r.areas()).allSatisfy(a -> assertThat(a.status())
                .isIn(AreaStatus.OK, AreaStatus.WARN, AreaStatus.CRITICAL));
    }

    @Test
    @DisplayName("generatedAt 페이로드가 채워진다")
    void generatedAt_is_present() {
        DashboardOverviewResult r = service.findOverview();
        assertThat(r.generatedAt()).isNotNull();
    }
}
