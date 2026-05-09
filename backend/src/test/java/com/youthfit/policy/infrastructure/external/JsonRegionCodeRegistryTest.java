package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.domain.model.RegionCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonRegionCodeRegistry")
class JsonRegionCodeRegistryTest {

    private JsonRegionCodeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new JsonRegionCodeRegistry(new ObjectMapper());
        registry.load();
    }

    @Test
    @DisplayName("서울 자치구 코드를 한글명으로 변환한다")
    void find_returnsSeoulGu() {
        Optional<RegionCode> gangnam = registry.find("11680");
        assertThat(gangnam).isPresent();
        assertThat(gangnam.get().sidoName()).isEqualTo("서울특별시");
        assertThat(gangnam.get().name()).isEqualTo("강남구");
    }

    @Test
    @DisplayName("의성군 등 비서울 시군구도 변환한다")
    void find_returnsNonSeoulSigungu() {
        Optional<RegionCode> uiseong = registry.find("47730");
        assertThat(uiseong).isPresent();
        assertThat(uiseong.get().sidoName()).isEqualTo("경상북도");
        assertThat(uiseong.get().name()).isEqualTo("의성군");
    }

    @Test
    @DisplayName("미등록 코드는 빈 결과를 반환한다")
    void find_unknownCode_returnsEmpty() {
        assertThat(registry.find("99999")).isEmpty();
    }

    @Test
    @DisplayName("여러 코드 중 매핑되는 것만 반환하고 순서를 유지한다")
    void findAll_filtersUnknownAndPreservesOrder() {
        List<RegionCode> result = registry.findAll(List.of("11680", "99999", "47730", "26350"));
        assertThat(result).extracting(RegionCode::name)
                .containsExactly("강남구", "의성군", "해운대구");
    }

    @Test
    @DisplayName("정책 76번처럼 전국 단위로 박힌 zipCd도 모두 매핑된다")
    void findAll_handlesNationwideZipCodes() {
        List<String> nationwide = List.of(
                "11110", "11680", "11740", "26110", "26710",
                "27720", "28177", "29200", "30200", "31710",
                "36110", "41591", "43114", "44825", "46910",
                "47730", "48121", "50130", "51810", "52111"
        );
        List<RegionCode> result = registry.findAll(nationwide);
        assertThat(result).hasSize(nationwide.size());
        assertThat(result).extracting(RegionCode::sidoCode)
                .containsExactly(
                        "11", "11", "11", "26", "26",
                        "27", "28", "29", "30", "31",
                        "36", "41", "43", "44", "46",
                        "47", "48", "50", "51", "52"
                );
    }

    @Test
    @DisplayName("null 입력은 빈 리스트로 처리한다")
    void findAll_nullInput_returnsEmptyList() {
        assertThat(registry.findAll(null)).isEmpty();
    }
}
