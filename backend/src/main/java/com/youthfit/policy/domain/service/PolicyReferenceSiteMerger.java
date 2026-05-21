package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class PolicyReferenceSiteMerger {

    public List<PolicyReferenceSite> merge(List<PolicyReferenceSite> existing,
                                           List<PolicyReferenceSite> adminInputs) {
        LinkedHashMap<String, PolicyReferenceSite> byUrl = new LinkedHashMap<>();

        // ADMIN 입력을 먼저 (중복 시 첫 번째 유지)
        for (PolicyReferenceSite s : adminInputs) {
            if (s == null || s.url() == null) continue;
            byUrl.putIfAbsent(
                    s.url(),
                    new PolicyReferenceSite(s.name(), s.url(), PolicyReferenceSiteSource.ADMIN)
            );
        }

        // 기존 사이트를 뒤에 (ADMIN 으로 들어간 URL 은 그대로)
        for (PolicyReferenceSite s : existing) {
            if (s == null || s.url() == null) continue;
            byUrl.putIfAbsent(s.url(), s);
        }

        return new ArrayList<>(byUrl.values());
    }
}
