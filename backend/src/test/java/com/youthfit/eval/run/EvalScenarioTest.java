package com.youthfit.eval.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EvalScenario")
class EvalScenarioTest {

    @Test
    @DisplayName("baseline 은 overrides 없음(null) — 운영 기본값 그대로")
    void baselineHasNoOverrides() {
        EvalScenario s = EvalScenario.of("baseline");
        assertThat(s.overrides()).isNull();
        assertThat(s.queryRewrite()).isFalse();
    }

    @Test
    @DisplayName("hybrid-on 은 hybridEnabled=true 만 덮어쓴다")
    void hybridOn() {
        EvalScenario s = EvalScenario.of("hybrid-on");
        assertThat(s.overrides().hybridEnabled()).isTrue();
        assertThat(s.overrides().keywordBoostEnabled()).isNull();
    }

    @Test
    @DisplayName("boost-off 는 keywordBoostEnabled=false 만 덮어쓴다")
    void boostOff() {
        EvalScenario s = EvalScenario.of("boost-off");
        assertThat(s.overrides().keywordBoostEnabled()).isFalse();
        assertThat(s.overrides().hybridEnabled()).isNull();
    }

    @Test
    @DisplayName("rewrite-on 은 쿼리 재작성 플래그만 켠다")
    void rewriteOn() {
        EvalScenario s = EvalScenario.of("rewrite-on");
        assertThat(s.queryRewrite()).isTrue();
        assertThat(s.overrides()).isNull();
    }

    @Test
    @DisplayName("알 수 없는 시나리오명은 예외")
    void unknownNameThrows() {
        assertThatThrownBy(() -> EvalScenario.of("없는시나리오"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는시나리오");
    }
}
