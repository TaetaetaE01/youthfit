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

    @Test
    void toContextText_사람이_읽을_수_있는_포맷_생성() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_208L, 100, 2_392_013L)),
                Map.of(HouseholdSize.ONE, 1_196_007L, HouseholdSize.TWO, 1_966_329L));

        String ctx = ref.toContextText();

        assertThat(ctx).contains("[참고 - 환산표 (2025년 기준)]");
        assertThat(ctx).contains("60%=143.5만");
        assertThat(ctx).contains("100%=239.2만");
        assertThat(ctx).contains("차상위계층");
        assertThat(ctx).contains("1인=119.6만");
    }

    @Test
    void findUrbanWorkerAmount_100퍼_기준값에_percent_비례하여_반환() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2026, 2,
                Map.of(),
                Map.of(),
                Map.of(HouseholdSize.FOUR, 8_802_202L));

        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.FOUR, 100))
                .contains(8_802_202L);
        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.FOUR, 130))
                .contains(11_442_863L);
        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.FOUR, 50))
                .contains(4_401_101L);
    }

    @Test
    void findUrbanWorkerAmount_미존재_가구원수면_빈_옵셔널() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2026, 2, Map.of(), Map.of(), Map.of());

        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.FOUR, 100)).isEmpty();
    }

    @Test
    void toContextText_urbanWorker_표도_노출() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2026, 2,
                Map.of(),
                Map.of(),
                Map.of(HouseholdSize.THREE, 8_168_429L, HouseholdSize.FOUR, 8_802_202L));

        String ctx = ref.toContextText();

        assertThat(ctx).contains("도시근로자 가구 월평균소득 100%");
        assertThat(ctx).contains("3인=816.8만");
        assertThat(ctx).contains("4인=880.2만");
        assertThat(ctx).contains("percent/100");
    }

    @Test
    void 기존_4_arg_생성자_호환_유지() {
        IncomeBracketReference ref = new IncomeBracketReference(
                2025, 1,
                Map.of(HouseholdSize.ONE, Map.of(60, 1_435_000L)),
                Map.of(HouseholdSize.ONE, 1_196_000L));

        assertThat(ref.urbanWorkerIncome()).isEmpty();
        assertThat(ref.findUrbanWorkerAmount(HouseholdSize.ONE, 100)).isEmpty();
    }
}
