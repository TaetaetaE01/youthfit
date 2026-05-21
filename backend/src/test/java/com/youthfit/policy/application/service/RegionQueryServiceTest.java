package com.youthfit.policy.application.service;

import com.youthfit.policy.application.dto.result.RegionListResult;
import com.youthfit.policy.application.port.RegionCodeRegistry;
import com.youthfit.policy.domain.model.RegionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("RegionQueryService")
@ExtendWith(MockitoExtension.class)
class RegionQueryServiceTest {

    @InjectMocks
    private RegionQueryService regionQueryService;

    @Mock
    private RegionCodeRegistry regionCodeRegistry;

    @Test
    @DisplayName("findAllRegions - 시·도 distinct + 시·군·구 전체 반환")
    void findAllRegions_returnsBothLists() {
        // given
        given(regionCodeRegistry.findAll()).willReturn(List.of(
                new RegionCode("11680", "11", "서울특별시", "강남구"),
                new RegionCode("11440", "11", "서울특별시", "마포구"),
                new RegionCode("26260", "26", "부산광역시", "동구")
        ));

        // when
        RegionListResult result = regionQueryService.findAllRegions();

        // then
        assertThat(result.sidos()).hasSize(2);
        assertThat(result.sidos()).extracting(RegionListResult.Sido::code)
                .containsExactly("11", "26");
        assertThat(result.sidos()).extracting(RegionListResult.Sido::name)
                .containsExactly("서울특별시", "부산광역시");
        assertThat(result.sigungus()).hasSize(3);
        assertThat(result.sigungus()).extracting(RegionListResult.Sigungu::code)
                .containsExactly("11680", "11440", "26260");
    }
}
