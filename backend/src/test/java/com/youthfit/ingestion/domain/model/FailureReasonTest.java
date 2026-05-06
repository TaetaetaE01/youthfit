package com.youthfit.ingestion.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FailureReasonTest {
    @Test
    void IllegalArgumentException_은_VALIDATION_으로_분류된다() {
        assertThat(FailureReason.classify(new IllegalArgumentException("bad"))).isEqualTo(FailureReason.VALIDATION);
    }
    @Test
    void RuntimeException_은_OTHER_로_분류된다() {
        assertThat(FailureReason.classify(new RuntimeException("x"))).isEqualTo(FailureReason.OTHER);
    }
    @Test
    void cause_체인을_따라_분류된다() {
        var inner = new IllegalArgumentException("bad");
        var outer = new RuntimeException("wrap", inner);
        assertThat(FailureReason.classify(outer)).isEqualTo(FailureReason.VALIDATION);
    }
    @Test
    void null_은_OTHER() {
        assertThat(FailureReason.classify(null)).isEqualTo(FailureReason.OTHER);
    }
}
