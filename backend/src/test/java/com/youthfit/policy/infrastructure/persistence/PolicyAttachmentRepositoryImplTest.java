package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.policy.domain.model.AttachmentExtractionCounts;
import com.youthfit.policy.domain.model.AttachmentStatus;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.PolicyAttachment;
import com.youthfit.policy.domain.model.SkipReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PolicyAttachmentJpaRepositoryAdapter.class})
@Testcontainers
@DisplayName("PolicyAttachmentJpaRepositoryAdapter — 정책별 extraction 집계")
class PolicyAttachmentRepositoryImplTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureTestDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "CREATE EXTENSION IF NOT EXISTS vector");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private PolicyJpaRepository policyRepository;

    @Autowired
    private PolicyAttachmentJpaRepository attachmentRepository;

    @Autowired
    private PolicyAttachmentJpaRepositoryAdapter sut;

    @BeforeEach
    void setUp() {
        attachmentRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    @DisplayName("aggregateExtractionByPolicyIds — 정책별 total/downloaded/extracted 카운트가 올바르게 집계된다")
    void aggregateExtractionByPolicyIds_countsCorrectly() {
        // policy A 첨부 4개: EXTRACTED + EXTRACTED + DOWNLOADED + PENDING
        //   total=4, downloaded=3 (>=DOWNLOADED), extracted=2
        Policy policyA = savePolicyWithAttachments(
                "정책A",
                List.of(AttachmentStatus.EXTRACTED, AttachmentStatus.EXTRACTED,
                        AttachmentStatus.DOWNLOADED, AttachmentStatus.PENDING)
        );
        // policy B 첨부 1개: FAILED
        //   total=1, downloaded=1, extracted=0
        Policy policyB = savePolicyWithAttachments(
                "정책B",
                List.of(AttachmentStatus.FAILED)
        );

        Map<Long, AttachmentExtractionCounts> result =
                sut.aggregateExtractionByPolicyIds(List.of(policyA.getId(), policyB.getId()));

        assertThat(result.get(policyA.getId()))
                .isEqualTo(new AttachmentExtractionCounts(4L, 3L, 2L));
        assertThat(result.get(policyB.getId()))
                .isEqualTo(new AttachmentExtractionCounts(1L, 1L, 0L));
    }

    @Test
    @DisplayName("aggregateExtractionByPolicyIds — 첨부가 없는 정책은 결과 맵에서 누락된다")
    void aggregateExtractionByPolicyIds_excludesPoliciesWithoutAttachments() {
        Policy policyA = savePolicyWithAttachments("정책A", List.of(AttachmentStatus.EXTRACTED));
        Policy policyB = savePolicyWithAttachments("정책B", List.of());

        Map<Long, AttachmentExtractionCounts> result =
                sut.aggregateExtractionByPolicyIds(List.of(policyA.getId(), policyB.getId()));

        assertThat(result).containsKey(policyA.getId());
        assertThat(result).doesNotContainKey(policyB.getId());
    }

    @Test
    @DisplayName("aggregateExtractionByPolicyIds — 빈 입력 리스트는 빈 맵을 반환한다 (DB 호출 없음)")
    void aggregateExtractionByPolicyIds_emptyInput_returnsEmptyMap() {
        Map<Long, AttachmentExtractionCounts> result =
                sut.aggregateExtractionByPolicyIds(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("aggregateExtractionByPolicyIds — SKIPPED 도 downloaded 버킷에 포함된다")
    void aggregateExtractionByPolicyIds_skippedCountsAsDownloaded() {
        // EXTRACTING + SKIPPED + DOWNLOADING(PENDING 다음 단계) + PENDING
        // downloaded bucket = EXTRACTING + SKIPPED = 2
        Policy policy = savePolicyWithAttachments(
                "정책",
                List.of(AttachmentStatus.EXTRACTING, AttachmentStatus.SKIPPED,
                        AttachmentStatus.DOWNLOADING, AttachmentStatus.PENDING)
        );

        Map<Long, AttachmentExtractionCounts> result =
                sut.aggregateExtractionByPolicyIds(List.of(policy.getId()));

        // total=4, downloaded=(EXTRACTING + SKIPPED)=2, extracted=0
        assertThat(result.get(policy.getId()))
                .isEqualTo(new AttachmentExtractionCounts(4L, 2L, 0L));
    }

    private Policy savePolicyWithAttachments(String title, List<AttachmentStatus> statuses) {
        Policy policy = Policy.builder()
                .title(title)
                .summary("test")
                .category(Category.HOUSING)
                .regionCode("전국")
                .build();
        List<PolicyAttachment> attachments = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            attachments.add(attachmentWithStatus("file-" + i + ".pdf",
                    "https://example.com/" + i, statuses.get(i)));
        }
        policy.replaceAttachments(attachments);
        return policyRepository.save(policy);
    }

    /**
     * Builder 는 PENDING 으로만 생성 가능하므로 상태 전이 메서드로 원하는 status 까지 이동시킨다.
     */
    private PolicyAttachment attachmentWithStatus(String name, String url, AttachmentStatus target) {
        PolicyAttachment a = PolicyAttachment.builder()
                .name(name)
                .url(url)
                .mediaType("application/pdf")
                .build();
        switch (target) {
            case PENDING -> { /* no-op */ }
            case DOWNLOADING -> a.markDownloading();
            case DOWNLOADED -> {
                a.markDownloading();
                a.markDownloaded("storage/" + name, "hash-" + name);
            }
            case EXTRACTING -> {
                a.markDownloading();
                a.markDownloaded("storage/" + name, "hash-" + name);
                a.markExtracting();
            }
            case EXTRACTED -> {
                a.markDownloading();
                a.markDownloaded("storage/" + name, "hash-" + name);
                a.markExtracting();
                a.markExtracted("extracted text");
            }
            case FAILED -> {
                a.markDownloading();
                a.markDownloaded("storage/" + name, "hash-" + name);
                a.markFailed("test failure");
            }
            case SKIPPED -> a.markSkipped(SkipReason.UNSUPPORTED_MIME);
        }
        return a;
    }
}
