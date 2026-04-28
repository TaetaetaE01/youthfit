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
        assertThat(ref.get().version()).isEqualTo(1);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 60)).contains(1_435_208L);
        assertThat(ref.get().findAmount(HouseholdSize.TWO, 100)).contains(3_932_658L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.ONE)).isEqualTo(1_196_007L);
    }

    @Test
    void findByYear_2026_yaml도_로드() {
        YamlIncomeBracketReferenceLoader loader = new YamlIncomeBracketReferenceLoader();
        loader.load();

        Optional<IncomeBracketReference> ref = loader.findByYear(2026);

        assertThat(ref).isPresent();
        assertThat(ref.get().year()).isEqualTo(2026);
        assertThat(ref.get().version()).isEqualTo(1);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 100)).contains(2_564_238L);
        assertThat(ref.get().findAmount(HouseholdSize.TWO, 100)).contains(4_199_292L);
        assertThat(ref.get().findAmount(HouseholdSize.ONE, 60)).contains(1_538_543L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.ONE)).isEqualTo(1_282_119L);
        assertThat(ref.get().nearPoor().get(HouseholdSize.TWO)).isEqualTo(2_099_646L);
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
