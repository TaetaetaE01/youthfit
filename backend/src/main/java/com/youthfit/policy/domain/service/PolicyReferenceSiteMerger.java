package com.youthfit.policy.domain.service;

import com.youthfit.policy.domain.model.PolicyReferenceSite;
import com.youthfit.policy.domain.model.PolicyReferenceSiteSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class PolicyReferenceSiteMerger {

    /**
     * 정책의 참조 사이트 목록을 관리자 입력과 병합하여 반환한다.
     *
     * <p><b>계약</b>
     * <ul>
     *   <li>{@code existing} — 정책에 현재 저장된 {@code referenceSites} 목록. 출처가 AUTO·ADMIN 혼재 가능.</li>
     *   <li>{@code adminInputs} — 관리자가 직접 입력한 항목. source 값에 무관하게 {@code ADMIN}으로 강제 태깅된다.</li>
     * </ul>
     *
     * <p><b>우선순위</b>: ADMIN &gt; AUTO.
     * 동일 URL이 adminInputs와 existing 양쪽에 존재하면 ADMIN 항목이 앞에 오며, existing 항목은 무시된다.
     *
     * <p><b>중복 제거</b>: URL 기준으로 중복을 제거한다.
     * adminInputs 내 동일 URL이 여러 번 등장하면 첫 번째 항목만 유지되며,
     * existing 항목은 이미 등장한 URL이면 건너뛴다.
     *
     * @param existing    정책의 현재 참조 사이트 목록 (null 요소 허용, 무시됨)
     * @param adminInputs 관리자가 입력한 참조 사이트 목록 (null 요소 허용, 무시됨)
     * @return ADMIN 항목이 앞에 오는 병합된 참조 사이트 목록
     */
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
