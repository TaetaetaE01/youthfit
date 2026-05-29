package com.youthfit.rag.application.listener;

import com.youthfit.common.event.PolicyUpsertedEvent;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyProcessingStep;
import com.youthfit.policy.domain.model.ProcessingStatus;
import com.youthfit.policy.domain.model.ProcessingStep;
import com.youthfit.policy.domain.repository.PolicyProcessingStepRepository;
import com.youthfit.policy.domain.repository.PolicyRepository;
import com.youthfit.rag.application.dto.command.IndexPolicyDocumentCommand;
import com.youthfit.rag.application.dto.result.IndexingResult;
import com.youthfit.rag.application.service.RagIndexingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #126 회귀 — {@link RagIndexingEventListener} 가 {@link PolicyUpsertedEvent} 에
 * 정상 반응하여 {@code RAG_INDEXING} step 행을 종결 상태로 기록하는지 보장한다.
 *
 * <p>오늘(2026-05-28) 의 조사에서 발견된 "silent fail" 증상 — 신규 정책 적재 후
 * RAG 인덱싱 단계가 로그 없이 동작하지 않는 회귀 — 의 재발 방지용 테스트다.
 *
 * <p>본 프로젝트는 임베디드 DB 인프라(H2 등)가 없어 {@code @SpringBootTest} 가
 * PostgreSQL 연결을 시도하다 실패한다. 또한 {@code policy.reference_sites/enrichment}
 * 등이 PostgreSQL jsonb 타입이라 H2 로 대체할 수 없다. 운영 PostgreSQL 환경에서
 * {@code @Disabled} 를 제거하면 이 테스트가 그대로 통과해야 한다.
 *
 * <p>외부 의존 ({@link RagIndexingService} — 내부에서 OpenAI 임베딩 호출) 은
 * {@link MockitoBean} 으로 대체해 OpenAI 호출 없이 listener 가 step 을 기록하는지
 * 만 검증한다.
 */
@Disabled("외부 인프라(PostgreSQL) 연결이 필요하여 CI에서 제외 — 실 DB 환경 갖춰지면 @Disabled 제거")
@SpringBootTest
@DisplayName("RAG 인덱싱 listener 회귀 (#126)")
class RagIndexingRegressionTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private PolicyProcessingStepRepository stepRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @MockitoBean
    private RagIndexingService ragIndexingService;

    @Test
    @DisplayName("PolicyUpsertedEvent 발행 시 RAG_INDEXING step 이 종결 상태로 기록된다")
    void RAG_indexing_listener_가_PolicyUpsertedEvent_에_정상_반응한다() {
        // given — 본문 있는 정책 1건 저장 + RagIndexingService stub
        Long policyId = saveTestPolicyWithBody();
        when(ragIndexingService.indexPolicyDocument(any()))
                .thenReturn(new IndexingResult(policyId, 3, true));

        // when — TX commit 후 AFTER_COMMIT listener 발화 보장을 위해 TX 안에서 publish
        txTemplate.execute(status -> {
            publisher.publishEvent(new PolicyUpsertedEvent(policyId, "회귀 테스트"));
            return null;
        });

        // then — listener 가 실제로 발화해 RAG_INDEXING step 이 종결 상태로 기록되어야 함
        await().atMost(ofSeconds(10)).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            Optional<PolicyProcessingStep> stepOpt =
                    stepRepository.findLatestByPolicyIdAndStep(policyId, ProcessingStep.RAG_INDEXING);
            assertThat(stepOpt).as("RAG_INDEXING step 행이 기록되어야 한다").isPresent();
            assertThat(stepOpt.get().getStatus())
                    .as("종결 상태(SUCCESS/FAILED/SKIPPED) 여야 한다 — IN_PROGRESS 면 listener 가 발화하지 않은 것")
                    .isIn(ProcessingStatus.SUCCESS, ProcessingStatus.FAILED, ProcessingStatus.SKIPPED);
        });

        // 그리고 stub 된 RagIndexingService.indexPolicyDocument 가 호출됐어야 함 — listener 가 진짜로 동작한 증거
        verify(ragIndexingService).indexPolicyDocument(any(IndexPolicyDocumentCommand.class));
    }

    /**
     * 회귀 테스트용 최소 Policy 를 생성하고 영속화한다.
     * body 가 있어야 listener 가 SKIPPED 가 아닌 실제 인덱싱 경로를 탄다.
     */
    private Long saveTestPolicyWithBody() {
        Policy policy = Policy.builder()
                .title("회귀 테스트용 정책")
                .summary("RAG 인덱싱 #126 회귀 검증용")
                .body("정책 본문입니다.")
                .category(Category.HOUSING)
                .regionCode("11000")
                .referenceYear(2026)
                .build();
        return policyRepository.save(policy).getId();
    }
}
