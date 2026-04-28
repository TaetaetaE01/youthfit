package com.youthfit.policy.domain.model;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class IncomeBracketReferenceTest {

    @Test
    void findAmount_가구원수와_퍼센트가_매칭되면_금액을_반환() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_000L)),
                Map.of(HouseholdSize.ONE, 1_196_000L));

        assertThat(ref.findAmount(HouseholdSize.ONE, 60)).contains(1_435_000L);
    }

    @Test
    void findAmount_미존재_퍼센트면_빈_옵셔널() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_000L)),
                Map.of());

        assertThat(ref.findAmount(HouseholdSize.ONE, 80)).isEmpty();
    }

    @Test
    void findAmount_미존재_가구원수면_빈_옵셔널() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1, Map.of(), Map.of());

        assertThat(ref.findAmount(HouseholdSize.ONE, 60)).isEmpty();
    }
}
