package com.youthfit.rag.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDocumentSourceTest {

    @Test
    void enum_은_3개_값을_가진다() {
        assertThat(PolicyDocumentSource.values())
                .containsExactly(
                        PolicyDocumentSource.BODY,
                        PolicyDocumentSource.ATTACHMENT,
                        PolicyDocumentSource.ENRICHMENT_BODY);
    }

    @Test
    void name_은_DB_저장_문자열과_일치한다() {
        assertThat(PolicyDocumentSource.BODY.name()).isEqualTo("BODY");
        assertThat(PolicyDocumentSource.ATTACHMENT.name()).isEqualTo("ATTACHMENT");
        assertThat(PolicyDocumentSource.ENRICHMENT_BODY.name()).isEqualTo("ENRICHMENT_BODY");
    }
}
