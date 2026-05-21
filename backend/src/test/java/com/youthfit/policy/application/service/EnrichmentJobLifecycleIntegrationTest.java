package com.youthfit.policy.application.service;

import com.youthfit.policy.application.port.ForceEnrichTrigger;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.EnrichmentJob;
import com.youthfit.policy.domain.model.EnrichmentJobStatus;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.repository.EnrichmentJobRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.policy.infrastructure.scheduler.EnrichmentJobTimeoutScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;

/**
 * EnrichmentJob 라이프사이클(PENDING → RUNNING → SUCCESS) + 동시 생성 차단 통합 테스트.
 *
 * <p>본 프로젝트는 임베디드 DB 인프라(H2 등)가 없어 {@code @SpringBootTest} 가
 * PostgreSQL 연결을 시도하다 실패한다(예: {@code LlmCallRecordedListenerIntegrationTest}).
 * 또한 {@code policy.reference_sites} 가 PostgreSQL jsonb 타입이라 H2 로 대체할 수도 없다.
 *
 * <p>운영 PostgreSQL 이 갖춰진 E2E 검증 환경에서 {@code @Disabled} 를 제거하면
 * 아래 두 테스트가 그대로 통과해야 한다.
 *
 * <p>외부 의존(n8n force-enrich webhook) 은 {@link ForceEnrichTrigger} 포트를 통해 호출되므로
 * {@link MockitoBean} 으로 대체해 테스트 격리를 보장한다.
 */
@Disabled("외부 인프라(PostgreSQL) 연결이 필요하여 CI에서 제외 — 실 DB 환경 갖춰지면 @Disabled 제거")
@SpringBootTest
@DisplayName("EnrichmentJob 라이프사이클 통합 테스트")
class EnrichmentJobLifecycleIntegrationTest {

    @Autowired
    EnrichmentJobService service;

    @Autowired
    EnrichmentJobRepository jobRepo;

    @Autowired
    PolicyRepository policyRepo;

    @Autowired
    EnrichmentJobTimeoutScheduler scheduler;

    @MockitoBean
    ForceEnrichTrigger trigger;

    @Test
    @DisplayName("PENDING → RUNNING → SUCCESS 라이프사이클")
    void PENDING_RUNNING_SUCCESS_라이프사이클() {
        doNothing().when(trigger).forceEnrich(anyLong(), anyLong(), anyList());
        Policy policy = savePolicyWithReferenceSites();

        EnrichmentJob created = service.create(policy.getId(), "admin@youthfit", null);
        assertThat(created.getStatus()).isEqualTo(EnrichmentJobStatus.PENDING);

        service.markRunning(created.getId());
        assertThat(jobRepo.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrichmentJobStatus.RUNNING);

        service.complete(created.getId(), EnrichmentJobStatus.SUCCESS, null);
        assertThat(jobRepo.findById(created.getId()).orElseThrow().getStatus())
                .isEqualTo(EnrichmentJobStatus.SUCCESS);
    }

    @Test
    @DisplayName("동일 정책에 진행 중 잡이 있으면 동시 생성은 409 (unique 인덱스 + service guard)")
    void 동일정책_동시_생성은_unique_index로_막힌다() {
        doNothing().when(trigger).forceEnrich(anyLong(), anyLong(), anyList());
        Policy policy = savePolicyWithReferenceSites();

        service.create(policy.getId(), "admin@youthfit", null);

        assertThatThrownBy(() -> service.create(policy.getId(), "admin@youthfit", null))
                .isInstanceOf(EnrichmentJobConflictException.class);
    }

    /**
     * 통합 테스트용 최소 Policy 를 생성하고 영속화한다.
     * referenceSites 는 EnrichmentJobService.create 가 빈 리스트를 거절하므로 1개 이상 필요하다.
     */
    private Policy savePolicyWithReferenceSites() {
        Policy policy = Policy.builder()
                .title("통합 테스트용 정책")
                .summary("리프레시 라이프사이클 검증용")
                .body("body")
                .category(Category.HOUSING)
                .regionCode("11000")
                .referenceYear(2026)
                .build();
        policy.replaceReferenceSites(List.of(
                PolicyReferenceSite.auto("기존", "https://example.com/policy")));
        return policyRepo.save(policy);
    }
}
