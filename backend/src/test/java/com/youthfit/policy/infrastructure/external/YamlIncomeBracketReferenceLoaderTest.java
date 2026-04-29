package com.youthfit.policy.infrastructure.external;

import com.youthfit.policy.domain.model.HouseholdSize;
import com.youthfit.policy.domain.model.IncomeBracketReference;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class YamlIncomeBracketReferenceLoaderTest {

    @Test
    void findByYear_2025_yaml_로드() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        Optional<IncomeBracketReference> ref = loader.findByYear(2025);

        assertThat(ref).isPresent();
        assertThat(ref.get().year()).isEqualTo(2025);
        assertThat(ref.get().version()).isEqualTo(2);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 60)).contains(1_435_208L);
        assertThat(ref.get().findAmount(HouseholdSize.TWO, 100)).contains(3_932_658L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.ONE)).isEqualTo(1_196_007L);
        assertThat(ref.get().urbanWorkerIncome().get(HouseholdSize.FOUR)).isEqualTo(8_802_202L);
        assertThat(ref.get().urbanWorkerIncome().get(HouseholdSize.THREE)).isEqualTo(8_168_429L);
    }

    @Test
    void findByYear_2026_yaml도_로드() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        Optional<IncomeBracketReference> ref = loader.findByYear(2026);

        assertThat(ref).isPresent();
        assertThat(ref.get().year()).isEqualTo(2026);
        assertThat(ref.get().version()).isEqualTo(2);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 100)).contains(2_564_238L);
        assertThat(ref.get().findAmount(HouseholdSize.TWO, 100)).contains(4_199_292L);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 60)).contains(1_538_543L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.ONE)).isEqualTo(1_282_119L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.TWO)).isEqualTo(2_099_646L);
        assertThat(ref.get().urbanWorkerIncome().get(HouseholdSize.FOUR)).isEqualTo(8_802_202L);
    }

    @Test
    void urbanWorker_비율은_100퍼_기준값에_percent_곱한값을_반환한다() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        IncomeBracketReference ref = loader.findByYear(2026).orElseThrow();

        // 4인 100% = 8,802,202원 → 130% = 11,442,863원 (반올림)
        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.FOUR, 130))
                .contains(11_442_863L);
        // 3인 100% = 8,168,429원 → 70% = 5,717,900원 (반올림)
        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.THREE, 70))
                .contains(5_717_900L);
    }

    @Test
    void findByYear_미존재면_빈_옵셔널() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        assertThat(loader.findByYear(1999)).isEmpty();
    }

    @Test
    void findLatest_2026이_최신() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        IncomeBracketReference latest = loader.findLatest();

        assertThat(latest.year()).isEqualTo(2026);
    }
}
