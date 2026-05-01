package com.youthfit.qna.application.dto.command;

import com.youthfit.policy.domain.model.Category;
import com.youthfit.policy.domain.model.Policy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyMetadata.from")
class PolicyMetadataTest {

    @Nested
    @DisplayName("Policy 9필드를 record 로 매핑")
    class HappyPath {

        @Test
        @DisplayName("모든 필드가 채워진 Policy 는 모든 필드가 매핑된다")
        void allFieldsMapped() {
            Policy policy = Policy.builder()
                    .title("청년내일저축계좌")
                    .summary("저소득 청년 자산형성 지원")
                    .body("본문")
                    .supportTarget("만 19~34세, 근로소득자")
                    .selectionCriteria("선정 기준")
                    .supportContent("월 30만원 매칭")
                    .organization("보건복지부")
                    .contact("02-123-4567")
                    .category(Category.WELFARE)
                    .regionCode("00")
                    .applyStart(LocalDate.of(2026, 5, 1))
                    .applyEnd(LocalDate.of(2026, 5, 31))
                    .referenceYear(2026)
                    .supportCycle("매월")
                    .provideType("현금")
                    .build();

            PolicyMetadata metadata = PolicyMetadata.from(policy);

            assertThat(metadata.category()).isEqualTo("WELFARE");
            assertThat(metadata.summary()).isEqualTo("저소득 청년 자산형성 지원");
            assertThat(metadata.supportTarget()).isEqualTo("만 19~34세, 근로소득자");
            assertThat(metadata.supportContent()).isEqualTo("월 30만원 매칭");
            assertThat(metadata.organization()).isEqualTo("보건복지부");
            assertThat(metadata.contact()).isEqualTo("02-123-4567");
            assertThat(metadata.applyStart()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(metadata.applyEnd()).isEqualTo(LocalDate.of(2026, 5, 31));
            assertThat(metadata.provideType()).isEqualTo("현금");
        }
    }

    @Nested
    @DisplayName("null 필드 처리")
    class NullFields {

        @Test
        @DisplayName("nullable 필드가 null 이면 record 필드도 null")
        void nullableFieldsRemainNull() {
            Policy policy = Policy.builder()
                    .title("최소 정보 정책")
                    .category(Category.JOBS)
                    .build();

            PolicyMetadata metadata = PolicyMetadata.from(policy);

            assertThat(metadata.category()).isEqualTo("JOBS");
            assertThat(metadata.summary()).isNull();
            assertThat(metadata.supportTarget()).isNull();
            assertThat(metadata.supportContent()).isNull();
            assertThat(metadata.organization()).isNull();
            assertThat(metadata.contact()).isNull();
            assertThat(metadata.applyStart()).isNull();
            assertThat(metadata.applyEnd()).isNull();
            assertThat(metadata.provideType()).isNull();
        }
    }
}
