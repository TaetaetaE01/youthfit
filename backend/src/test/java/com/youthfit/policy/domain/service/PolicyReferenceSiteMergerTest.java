package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyReferenceSiteMergerTest {

    private final PolicyReferenceSiteMerger merger = new PolicyReferenceSiteMerger();

    @Test
    void ADMIN_입력이_AUTO_보존하면서_추가된다() {
        var existing = List.of(PolicyReferenceSite.auto("기존", "https://a.example.com"));
        var adminInputs = List.of(PolicyReferenceSite.admin("새URL", "https://b.example.com"));

        var merged = merger.merge(existing, adminInputs);

        assertThat(merged).extracting(PolicyReferenceSite::url)
                .containsExactly("https://b.example.com", "https://a.example.com");
        assertThat(merged.get(0).source()).isEqualTo(PolicyReferenceSiteSource.ADMIN);
        assertThat(merged.get(1).source()).isEqualTo(PolicyReferenceSiteSource.AUTO);
    }

    @Test
    void 같은URL은_AUTO에서_ADMIN으로_승격된다() {
        var existing = List.of(PolicyReferenceSite.auto("기존", "https://a.example.com"));
        var adminInputs = List.of(PolicyReferenceSite.admin("재확인", "https://a.example.com"));

        var merged = merger.merge(existing, adminInputs);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).source()).isEqualTo(PolicyReferenceSiteSource.ADMIN);
        assertThat(merged.get(0).name()).isEqualTo("재확인");
    }

    @Test
    void 같은URL중복입력은_제거된다() {
        var adminInputs = List.of(
                PolicyReferenceSite.admin("A", "https://x.example.com"),
                PolicyReferenceSite.admin("A2", "https://x.example.com"));

        var merged = merger.merge(List.of(), adminInputs);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).name()).isEqualTo("A");
    }

    @Test
    void 정렬은_ADMIN우선_그다음_AUTO() {
        var existing = List.of(
                PolicyReferenceSite.auto("A", "https://a.example.com"),
                PolicyReferenceSite.auto("B", "https://b.example.com"));
        var adminInputs = List.of(PolicyReferenceSite.admin("Z", "https://z.example.com"));

        var merged = merger.merge(existing, adminInputs);

        assertThat(merged).extracting(PolicyReferenceSite::source)
                .containsExactly(
                        PolicyReferenceSiteSource.ADMIN,
                        PolicyReferenceSiteSource.AUTO,
                        PolicyReferenceSiteSource.AUTO);
    }
}
