package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicySource Entity")
class PolicySourceTest {

    @Nested
    @DisplayName("hasChanged - 소스 변경 감지")
    class HasChanged {

        @Test
        @DisplayName("해시가 다르면 변경된 것으로 판단한다")
        void differentHash_returnsTrue() {
            // given
            PolicySource source = createMockSource("hash-v1");

            // when & then
            assertThat(source.hasChanged("hash-v2")).isTrue();
        }

        @Test
        @DisplayName("해시가 같으면 변경되지 않은 것으로 판단한다")
        void sameHash_returnsFalse() {
            // given
            PolicySource source = createMockSource("hash-v1");

            // when & then
            assertThat(source.hasChanged("hash-v1")).isFalse();
        }
    }

    @Test
    @DisplayName("소스를 업데이트하면 rawJson과 해시가 갱신된다")
    void updateSource_updatesRawJsonAndHash() {
        // given
        PolicySource source = createMockSource("old-hash");

        // when
        source.updateSource("{\"new\":\"data\"}", "new-hash");

        // then
        assertThat(source.getRawJson()).isEqualTo("{\"new\":\"data\"}");
        assertThat(source.getSourceHash()).isEqualTo("new-hash");
    }

    // ── 헬퍼 메서드 ──

    private PolicySource createMockSource(String sourceHash) {
        Policy policy = Policy.builder()
                .title("테스트 정책")
                .category(Category.JOBS)
                .build();

        return PolicySource.builder()
                .policy(policy)
                .sourceType(SourceType.YOUTH_SEOUL_CRAWL)
                .externalId("ext-001")
                .sourceUrl("https://example.com/policy/1")
                .rawJson("{\"original\":\"data\"}")
                .sourceHash(sourceHash)
                .build();
    }
}
