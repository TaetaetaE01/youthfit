package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegionFilter")
class RegionFilterTest {

    @Nested
    @DisplayName("of(codes)")
    class Of {
        @Test
        @DisplayName("null 입력은 비활성 필터를 반환한다 — 전체 정책 조회")
        void nullCodes_returnsInactive() {
            RegionFilter filter = RegionFilter.of(null);
            assertThat(filter.isActive()).isFalse();
        }

        @Test
        @DisplayName("빈 리스트도 비활성")
        void emptyCodes_returnsInactive() {
            assertThat(RegionFilter.of(List.of()).isActive()).isFalse();
        }

        @Test
        @DisplayName("NATIONWIDE 단독 — 전국만 보기 모드")
        void nationwideAlone_returnsNationwideOnly() {
            RegionFilter filter = RegionFilter.of(List.of("NATIONWIDE"));
            assertThat(filter.isActive()).isTrue();
            assertThat(filter.isNationwideOnly()).isTrue();
            assertThat(filter.sidoCodes()).isEmpty();
            assertThat(filter.sigunguCodes()).isEmpty();
        }

        @Test
        @DisplayName("한글 별칭 '전국' 도 NATIONWIDE 와 같이 인식한다")
        void koreanNationwide_treatedSame() {
            assertThat(RegionFilter.of(List.of("전국")).isNationwideOnly()).isTrue();
        }

        @Test
        @DisplayName("2자리 코드는 시·도로 분류된다")
        void twoDigitCode_classifiedAsSido() {
            RegionFilter filter = RegionFilter.of(List.of("11"));
            assertThat(filter.sidoCodes()).containsExactly("11");
            assertThat(filter.sigunguCodes()).isEmpty();
            assertThat(filter.isNationwideOnly()).isFalse();
        }

        @Test
        @DisplayName("5자리 코드는 시·군·구로 분류된다")
        void fiveDigitCode_classifiedAsSigungu() {
            RegionFilter filter = RegionFilter.of(List.of("11680"));
            assertThat(filter.sigunguCodes()).containsExactly("11680");
            assertThat(filter.sidoCodes()).isEmpty();
        }

        @Test
        @DisplayName("혼합 입력은 각각 분류된다")
        void mixed_classifiedSeparately() {
            RegionFilter filter = RegionFilter.of(List.of("11", "26260", "41"));
            assertThat(filter.sidoCodes()).containsExactly("11", "41");
            assertThat(filter.sigunguCodes()).containsExactly("26260");
        }

        @Test
        @DisplayName("알 수 없는 길이의 코드는 무시한다")
        void invalidLength_ignored() {
            RegionFilter filter = RegionFilter.of(List.of("1", "123", "1234567"));
            assertThat(filter.isActive()).isFalse();
        }

        @Test
        @DisplayName("숫자 아닌 코드는 무시한다 (NATIONWIDE 토큰 제외)")
        void nonDigit_ignored() {
            RegionFilter filter = RegionFilter.of(List.of("ABC", "11680"));
            assertThat(filter.sigunguCodes()).containsExactly("11680");
            assertThat(filter.sidoCodes()).isEmpty();
        }

        @Test
        @DisplayName("중복 코드는 1회만 반영한다")
        void duplicates_deduplicated() {
            RegionFilter filter = RegionFilter.of(List.of("11", "11", "11680", "11680"));
            assertThat(filter.sidoCodes()).containsExactly("11");
            assertThat(filter.sigunguCodes()).containsExactly("11680");
        }

        @Test
        @DisplayName("공백·null 항목은 무시한다")
        void blankItems_ignored() {
            java.util.List<String> input = new java.util.ArrayList<>();
            input.add("  11 ");
            input.add(null);
            input.add("");
            RegionFilter filter = RegionFilter.of(input);
            assertThat(filter.sidoCodes()).containsExactly("11");
        }

        @Test
        @DisplayName("NATIONWIDE 가 다른 코드와 함께 오면 일반 필터로 취급 (전국만 모드 아님)")
        void nationwideWithOthers_notNationwideOnly() {
            RegionFilter filter = RegionFilter.of(List.of("NATIONWIDE", "11"));
            assertThat(filter.isNationwideOnly()).isFalse();
            assertThat(filter.sidoCodes()).containsExactly("11");
        }
    }

    @Nested
    @DisplayName("ofCsv(csv)")
    class OfCsv {
        @Test
        @DisplayName("CSV 문자열을 파싱한다")
        void parsesCsv() {
            RegionFilter filter = RegionFilter.ofCsv("11,11680,26260");
            assertThat(filter.sidoCodes()).containsExactly("11");
            assertThat(filter.sigunguCodes()).containsExactly("11680", "26260");
        }

        @Test
        @DisplayName("null 또는 blank CSV 는 비활성")
        void nullOrBlank_inactive() {
            assertThat(RegionFilter.ofCsv(null).isActive()).isFalse();
            assertThat(RegionFilter.ofCsv("").isActive()).isFalse();
            assertThat(RegionFilter.ofCsv("   ").isActive()).isFalse();
        }
    }
}
