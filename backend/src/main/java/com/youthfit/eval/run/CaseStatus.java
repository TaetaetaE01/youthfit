package com.youthfit.eval.run;

public enum CaseStatus {
    OK,
    SKIPPED,     // 임베딩·검색 예외
    STALE,       // policyId-title 불일치 (시드 재구축 감지)
    NO_CHUNKS    // 대상 정책에 인덱싱된 청크 없음
}
