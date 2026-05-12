package com.youthfit.policy.infrastructure.persistence;

import com.youthfit.policy.domain.model.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("PolicySourceRepositoryImpl.findExternalIdHashMap")
@ExtendWith(MockitoExtension.class)
class PolicySourceRepositoryExternalHashesTest {

    @Mock
    private PolicySourceJpaRepository jpaRepository;

    @InjectMocks
    private PolicySourceRepositoryImpl sut;

    @Test
    @DisplayName("해당 sourceType의 데이터가 없으면 빈 Map 반환")
    void returns_empty_map_when_no_sources_for_type() {
        given(jpaRepository.findExternalIdAndHashBySourceType(SourceType.YOUTH_CENTER))
                .willReturn(Collections.emptyList());

        Map<String, String> result = sut.findExternalIdHashMap(SourceType.YOUTH_CENTER);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("YOUTH_CENTER 타입의 2개 row → externalId:sourceHash 맵 정확히 반환")
    void maps_external_id_to_source_hash_correctly() {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"ext-001", "hash-aaa"},
                new Object[]{"ext-002", "hash-bbb"}
        );
        given(jpaRepository.findExternalIdAndHashBySourceType(SourceType.YOUTH_CENTER))
                .willReturn(rows);

        Map<String, String> result = sut.findExternalIdHashMap(SourceType.YOUTH_CENTER);

        assertThat(result).hasSize(2);
        assertThat(result.get("ext-001")).isEqualTo("hash-aaa");
        assertThat(result.get("ext-002")).isEqualTo("hash-bbb");
    }

    @Test
    @DisplayName("YOUTH_CENTER 조회 시 다른 sourceType(BOKJIRO_CENTRAL) rows는 포함되지 않음")
    void excludes_sources_of_other_types() {
        Object[] row = {"ext-yc-001", "hash-yc-001"};
        List<Object[]> youthCenterRows = Collections.singletonList(row);
        given(jpaRepository.findExternalIdAndHashBySourceType(SourceType.YOUTH_CENTER))
                .willReturn(youthCenterRows);

        Map<String, String> result = sut.findExternalIdHashMap(SourceType.YOUTH_CENTER);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("ext-yc-001");
        assertThat(result).doesNotContainKey("ext-bokjiro-001");
    }
}
