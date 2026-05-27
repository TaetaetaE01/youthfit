package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.common.config.JpaAuditingConfig;
import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import com.youthfit.policy.domain.model.RegionFilter;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers
@DisplayName("PolicySpecification — 캘린더 범위 오버랩 + 상시 정책")
class PolicySpecificationCalendarTest {

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
    private PolicyJpaRepository repository;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("조회 범위에 완전히 포함된 정책")
    void fullyInsideRange() {
        Policy p = policyWithDates("내부", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 20));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1).extracting(Policy::getTitle).containsExactly("내부");
    }

    @Test
    @DisplayName("조회 범위 좌측에 걸친 정책 (시작이 from 이전, 마감이 범위 안)")
    void straddlesLeft() {
        Policy p = policyWithDates("좌측", LocalDate.of(2026, 2, 20), LocalDate.of(2026, 3, 10));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("조회 범위 우측에 걸친 정책 (시작이 범위 안, 마감이 to 이후)")
    void straddlesRight() {
        Policy p = policyWithDates("우측", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 10));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("조회 범위를 완전히 포함하는 정책")
    void containsRange() {
        Policy p = policyWithDates("포함", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("범위 밖 정책은 제외")
    void outsideRange() {
        repository.save(policyWithDates("이전", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        repository.save(policyWithDates("이후", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("applyStart=null, applyEnd>=from 이면 포함 (마감만 있는 경우)")
    void nullStartButEndInsideRange() {
        Policy p = policyWithDates("시작null", null, LocalDate.of(2026, 3, 15));
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("applyEnd=null, applyStart<=to 이면 포함 (시작만 있는 경우)")
    void nullEndButStartInsideRange() {
        Policy p = policyWithDates("끝null", LocalDate.of(2026, 3, 15), null);
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("applyStart=null AND applyEnd=null (상시 정책) 은 calendar 에서 제외")
    void bothNullExcluded() {
        Policy p = policyWithDates("상시", null, null);
        repository.save(p);

        List<Policy> result = repository.findAll(
                PolicySpecification.withCalendarRange(FROM, TO, RegionFilter.of(null), null));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("alwaysOpen — 진짜 상시 (둘 다 null) 는 포함, 일반 기간 정책은 제외")
    void alwaysOpenReturnsOnlyBothNull() {
        repository.save(policyWithDates("상시1", null, null));
        repository.save(policyWithDates("상시2", null, null));
        repository.save(policyWithDates("기간있음", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        repository.save(policyWithDates("끝만있음", null, LocalDate.of(2026, 3, 31)));

        List<Policy> result = repository.findAll(
                PolicySpecification.alwaysOpen(RegionFilter.of(null), null));

        assertThat(result).hasSize(2)
                .extracting(Policy::getTitle)
                .containsExactlyInAnyOrder("상시1", "상시2");
    }

    @Test
    @DisplayName("alwaysOpen — 사실상 상시: end=12-31 AND span>=270일 정책을 포함")
    void alwaysOpenIncludesEffectivelyAlwaysOpen() {
        // 진짜 상시
        repository.save(policyWithDates("진짜상시", null, null));
        // 사실상 상시 (다양한 경계)
        repository.save(policyWithDates("연중1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        repository.save(policyWithDates("마감만", null, LocalDate.of(2026, 12, 31)));
        repository.save(policyWithDates("멀티year", LocalDate.of(2025, 12, 31), LocalDate.of(2026, 12, 31)));
        repository.save(policyWithDates("정확히270일", LocalDate.of(2026, 12, 31).minusDays(270), LocalDate.of(2026, 12, 31)));
        // 제외되어야 할 케이스
        repository.save(policyWithDates("span269", LocalDate.of(2026, 12, 31).minusDays(269), LocalDate.of(2026, 12, 31)));
        repository.save(policyWithDates("end11-30", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 30)));
        repository.save(policyWithDates("3월모집", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        List<Policy> result = repository.findAll(
                PolicySpecification.alwaysOpen(RegionFilter.of(null), null));

        assertThat(result)
                .extracting(Policy::getTitle)
                .containsExactlyInAnyOrder("진짜상시", "연중1", "마감만", "멀티year", "정확히270일");
    }

    @Test
    @DisplayName("alwaysOpen 은 referenceYear 가 현재 연도보다 이전인 만료 정책을 제외")
    void alwaysOpenExcludesExpiredReferenceYear() {
        int currentYear = LocalDate.now().getYear();
        repository.save(policyWithDatesAndYear("작년상시", null, null, currentYear - 1));
        repository.save(policyWithDatesAndYear("올해상시", null, null, currentYear));
        repository.save(policyWithDatesAndYear("연도없는상시", null, null, null));

        List<Policy> result = repository.findAll(
                PolicySpecification.alwaysOpen(RegionFilter.of(null), null));

        assertThat(result).hasSize(2)
                .extracting(Policy::getTitle)
                .containsExactlyInAnyOrder("올해상시", "연도없는상시");
    }

    private Policy policyWithDates(String title, LocalDate start, LocalDate end) {
        return Policy.builder()
                .title(title)
                .summary("test")
                .category(Category.HOUSING)
                .regionCode("전국")
                .applyStart(start)
                .applyEnd(end)
                .build();
    }

    private Policy policyWithDatesAndYear(String title, LocalDate start, LocalDate end, Integer year) {
        return Policy.builder()
                .title(title)
                .summary("test")
                .category(Category.HOUSING)
                .regionCode("전국")
                .applyStart(start)
                .applyEnd(end)
                .referenceYear(year)
                .build();
    }
}
