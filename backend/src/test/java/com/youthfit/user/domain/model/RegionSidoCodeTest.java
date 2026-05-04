package com.youthfit.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegionSidoCode")
class RegionSidoCodeTest {

    @Nested
    @DisplayName("matches")
    class Matches {

        @Test
        @DisplayName("null 또는 NATIONAL 정책 regionCode는 모든 시도와 매칭된다")
        void nationalAndNullMatchAll() {
            assertThat(RegionSidoCode.SEOUL.matches(null)).isTrue();
            assertThat(RegionSidoCode.SEOUL.matches("NATIONAL")).isTrue();
            assertThat(RegionSidoCode.GYEONGGI.matches(null)).isTrue();
        }

        @Test
        @DisplayName("영문 시도 코드와 매칭된다")
        void matchEnglishCode() {
            assertThat(RegionSidoCode.SEOUL.matches("SEOUL")).isTrue();
            assertThat(RegionSidoCode.SEOUL.matches("BUSAN")).isFalse();
        }

        @Test
        @DisplayName("법정동 prefix와 매칭된다")
        void matchLegalDongPrefix() {
            assertThat(RegionSidoCode.SEOUL.matches("11110000")).isTrue();
            assertThat(RegionSidoCode.SEOUL.matches("26110000")).isFalse();
            assertThat(RegionSidoCode.BUSAN.matches("26110000")).isTrue();
        }

        @Test
        @DisplayName("매칭되지 않는 임의 문자열은 false")
        void noMatch() {
            assertThat(RegionSidoCode.SEOUL.matches("foo")).isFalse();
        }
    }

    @Nested
    @DisplayName("displayName / legalDongPrefix")
    class Metadata {

        @Test
        @DisplayName("displayName과 legalDongPrefix가 정의되어 있다")
        void labelsAreDefined() {
            assertThat(RegionSidoCode.SEOUL.displayName()).isEqualTo("서울");
            assertThat(RegionSidoCode.SEOUL.legalDongPrefix()).isEqualTo("11");
            assertThat(RegionSidoCode.JEJU.displayName()).isEqualTo("제주");
            assertThat(RegionSidoCode.JEJU.legalDongPrefix()).isEqualTo("50");
        }

        @Test
        @DisplayName("17개 시/도가 모두 정의되어 있다")
        void allSeventeenSidos() {
            assertThat(RegionSidoCode.values()).hasSize(17);
        }
    }
}
