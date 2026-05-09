package com.youthfit.policy.application.dto.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyIngestionResultTest {

    @Test
    @DisplayName("registered 팩토리는 Outcome.REGISTERED 와 isNew()=true 를 반환한다")
    void registered() {
        PolicyIngestionResult r = PolicyIngestionResult.registered(1L);
        assertThat(r.outcome()).isEqualTo(PolicyIngestionResult.Outcome.REGISTERED);
        assertThat(r.isNew()).isTrue();
        assertThat(r.policyId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("updated 팩토리는 Outcome.UPDATED 와 isNew()=false 를 반환한다")
    void updated() {
        PolicyIngestionResult r = PolicyIngestionResult.updated(2L);
        assertThat(r.outcome()).isEqualTo(PolicyIngestionResult.Outcome.UPDATED);
        assertThat(r.isNew()).isFalse();
        assertThat(r.policyId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("skippedDuplicate 팩토리는 Outcome.SKIPPED_DUPLICATE 와 isNew()=false 를 반환한다")
    void skippedDuplicate() {
        PolicyIngestionResult r = PolicyIngestionResult.skippedDuplicate(3L);
        assertThat(r.outcome()).isEqualTo(PolicyIngestionResult.Outcome.SKIPPED_DUPLICATE);
        assertThat(r.isNew()).isFalse();
        assertThat(r.policyId()).isEqualTo(3L);
    }
}
