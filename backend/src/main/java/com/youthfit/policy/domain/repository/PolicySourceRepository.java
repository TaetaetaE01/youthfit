package com.youthfit.policy.domain.repository;

import com.youthfit.policy.domain.model.PolicySource;
import com.youthfit.policy.domain.model.SourceType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PolicySourceRepository {

    Optional<PolicySource> findBySourceTypeAndExternalId(SourceType sourceType, String externalId);

    Optional<PolicySource> findFirstByPolicyId(Long policyId);

    Map<Long, PolicySource> findFirstByPolicyIds(List<Long> policyIds);

    Map<String, String> findExternalIdHashMap(SourceType sourceType);

    /**
     * 정책별 출처 타입 목록 일괄 조회.
     * id 오름차순으로 모으며, 같은 정책에 동일 출처가 중복 등록돼도 한 번만 담는다.
     * 조회된 출처가 없는 정책은 결과 맵에 키가 없다(호출자가 빈 리스트로 기본 처리).
     */
    Map<Long, List<SourceType>> findSourceTypesByPolicyIds(List<Long> policyIds);

    PolicySource save(PolicySource policySource);
}
